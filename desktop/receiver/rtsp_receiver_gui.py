#!/usr/bin/env python3
"""
PhoneCam RTSP Receiver GUI - Receives H.264 stream and outputs to virtual camera

Features:
- Modern GUI with connection status
- Unity Capture virtual camera output
- Real-time FPS and bitrate display
- One-click start/stop
"""

import asyncio
import logging
import queue
import socket
import sys
import threading
import time
import tkinter as tk
from tkinter import ttk
from typing import Optional

import av
import numpy as np

try:
    import pyvirtualcam
    PYVIRTUALCAM_AVAILABLE = True
except ImportError:
    PYVIRTUALCAM_AVAILABLE = False

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)


class H264StreamDecoder:
    """Decodes H.264 Annex B stream"""
    
    def __init__(self):
        self.codec = av.CodecContext.create('h264', 'r')
        self.codec.thread_type = 'AUTO'
        self.codec.thread_count = 0
        self.frame_count = 0
        self.start_time = time.time()
        
    def decode(self, data: bytes) -> list:
        frames = []
        try:
            packet = av.Packet(data)
            for frame in self.codec.decode(packet):
                img = frame.to_ndarray(format='bgr24')
                frames.append(img)
                self.frame_count += 1
        except Exception as e:
            pass
        return frames
    
    @property
    def fps(self) -> float:
        elapsed = time.time() - self.start_time
        return self.frame_count / elapsed if elapsed > 0 else 0


class VirtualCamera:
    """Unity Capture virtual camera wrapper"""
    
    def __init__(self, width=1920, height=1080, fps=60):
        self.width = width
        self.height = height
        self.fps = fps
        self.camera = None
        
    def start(self) -> bool:
        if not PYVIRTUALCAM_AVAILABLE:
            return False
        try:
            self.camera = pyvirtualcam.Camera(
                width=self.width, height=self.height, fps=self.fps,
                fmt=pyvirtualcam.PixelFormat.BGR, backend='unitycapture'
            )
            logger.info(f"Virtual camera: {self.camera.device}")
            return True
        except Exception as e:
            logger.error(f"Virtual camera failed: {e}")
            return False
    
    def send(self, frame: np.ndarray):
        if self.camera:
            if frame.shape[1] != self.width or frame.shape[0] != self.height:
                import cv2
                frame = cv2.resize(frame, (self.width, self.height))
            self.camera.send(frame)
    
    def stop(self):
        if self.camera:
            self.camera.close()
            self.camera = None


class StreamServer:
    """TCP server for receiving H.264 stream"""
    
    def __init__(self, port: int, status_callback, stats_callback):
        self.port = port
        self.status_callback = status_callback
        self.stats_callback = stats_callback
        self.running = False
        self.server_socket = None
        self.client_socket = None
        self.decoder = None
        self.virtual_cam = None
        self.thread = None
        
    def start(self):
        self.running = True
        self.thread = threading.Thread(target=self._run_server, daemon=True)
        self.thread.start()
        
    def stop(self):
        self.running = False
        if self.client_socket:
            try:
                self.client_socket.close()
            except:
                pass
        if self.server_socket:
            try:
                self.server_socket.close()
            except:
                pass
                
    def _run_server(self):
        try:
            self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.server_socket.bind(('0.0.0.0', self.port))
            self.server_socket.listen(1)
            self.server_socket.settimeout(1.0)
            
            local_ip = self._get_local_ip()
            self.status_callback(f"Waiting for connection on {local_ip}:{self.port}")
            
            while self.running:
                try:
                    self.client_socket, addr = self.server_socket.accept()
                    self.client_socket.settimeout(5.0)
                    self.client_socket.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                    
                    logger.info(f"Client connected: {addr}")
                    self.status_callback(f"Connected: {addr[0]}")
                    
                    self._handle_client()
                    
                except socket.timeout:
                    continue
                except Exception as e:
                    if self.running:
                        logger.error(f"Accept error: {e}")
                        
        except Exception as e:
            logger.error(f"Server error: {e}")
            self.status_callback(f"Error: {e}")
        finally:
            if self.server_socket:
                self.server_socket.close()
            self.status_callback("Server stopped")
    
    def _handle_client(self):
        self.decoder = H264StreamDecoder()
        self.virtual_cam = VirtualCamera()
        
        if not self.virtual_cam.start():
            self.status_callback("Virtual camera failed! Install Unity Capture")
        
        buffer = bytearray()
        bytes_received = 0
        last_stats_time = time.time()
        
        try:
            while self.running:
                try:
                    data = self.client_socket.recv(65536)
                except socket.timeout:
                    continue
                    
                if not data:
                    break
                    
                buffer.extend(data)
                bytes_received += len(data)
                
                # Process NAL units
                while True:
                    start = self._find_start_code(buffer, 0)
                    if start < 0:
                        break
                    next_start = self._find_start_code(buffer, start + 4)
                    if next_start < 0:
                        break
                    
                    nal_data = bytes(buffer[start:next_start])
                    buffer = buffer[next_start:]
                    
                    frames = self.decoder.decode(nal_data)
                    for frame in frames:
                        self.virtual_cam.send(frame)
                
                # Update stats
                now = time.time()
                if now - last_stats_time >= 1.0:
                    mbps = (bytes_received * 8) / (1024 * 1024)
                    self.stats_callback(f"{self.decoder.fps:.1f} fps | {mbps:.1f} Mbps")
                    bytes_received = 0
                    last_stats_time = now
                    
        except Exception as e:
            if self.running:
                logger.error(f"Client error: {e}")
        finally:
            self.virtual_cam.stop()
            if self.client_socket:
                self.client_socket.close()
            self.status_callback("Disconnected - waiting for connection...")
            self.stats_callback("")
    
    def _find_start_code(self, data: bytearray, start: int) -> int:
        for i in range(start, len(data) - 3):
            if data[i] == 0 and data[i+1] == 0 and data[i+2] == 0 and data[i+3] == 1:
                return i
        return -1
    
    def _get_local_ip(self) -> str:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except:
            return "127.0.0.1"


class PhoneCamGUI:
    """Main GUI application"""
    
    def __init__(self):
        self.root = tk.Tk()
        self.root.title("PhoneCam RTSP Receiver")
        self.root.geometry("450x300")
        self.root.resizable(False, False)
        
        # Server
        self.server = None
        self.port = 5000
        
        self._create_ui()
        self._update_connection_info()
        
    def _create_ui(self):
        # Main frame with padding
        main = ttk.Frame(self.root, padding=20)
        main.pack(fill=tk.BOTH, expand=True)
        
        # Title
        title = ttk.Label(main, text="PhoneCam RTSP Receiver", font=('Segoe UI', 16, 'bold'))
        title.pack(pady=(0, 10))
        
        subtitle = ttk.Label(main, text="H.264 over TCP → Unity Capture", font=('Segoe UI', 10))
        subtitle.pack(pady=(0, 20))
        
        # Connection info frame
        info_frame = ttk.LabelFrame(main, text="Connection Info", padding=10)
        info_frame.pack(fill=tk.X, pady=(0, 15))
        
        self.ip_label = ttk.Label(info_frame, text="IP: Loading...", font=('Consolas', 11))
        self.ip_label.pack(anchor=tk.W)
        
        self.port_label = ttk.Label(info_frame, text=f"Port: {self.port}", font=('Consolas', 11))
        self.port_label.pack(anchor=tk.W)
        
        # Status frame
        status_frame = ttk.LabelFrame(main, text="Status", padding=10)
        status_frame.pack(fill=tk.X, pady=(0, 15))
        
        self.status_label = ttk.Label(status_frame, text="Ready to start", font=('Segoe UI', 10))
        self.status_label.pack(anchor=tk.W)
        
        self.stats_label = ttk.Label(status_frame, text="", font=('Consolas', 10), foreground='green')
        self.stats_label.pack(anchor=tk.W)
        
        # Buttons
        btn_frame = ttk.Frame(main)
        btn_frame.pack(fill=tk.X)
        
        self.start_btn = ttk.Button(btn_frame, text="Start Server", command=self._toggle_server)
        self.start_btn.pack(side=tk.LEFT, expand=True, fill=tk.X, padx=(0, 5))
        
        quit_btn = ttk.Button(btn_frame, text="Quit", command=self._quit)
        quit_btn.pack(side=tk.RIGHT, expand=True, fill=tk.X, padx=(5, 0))
        
        # Virtual camera warning
        if not PYVIRTUALCAM_AVAILABLE:
            warn = ttk.Label(main, text="⚠ pyvirtualcam not installed", foreground='red')
            warn.pack(pady=(10, 0))
    
    def _update_connection_info(self):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            self.ip_label.config(text=f"Enter on phone: {ip}:{self.port}")
        except:
            self.ip_label.config(text="IP: Unable to detect")
    
    def _toggle_server(self):
        if self.server is None:
            self._start_server()
        else:
            self._stop_server()
    
    def _start_server(self):
        self.server = StreamServer(
            self.port,
            lambda s: self.root.after(0, lambda: self.status_label.config(text=s)),
            lambda s: self.root.after(0, lambda: self.stats_label.config(text=s))
        )
        self.server.start()
        self.start_btn.config(text="Stop Server")
        self.status_label.config(text="Starting server...")
    
    def _stop_server(self):
        if self.server:
            self.server.stop()
            self.server = None
        self.start_btn.config(text="Start Server")
        self.status_label.config(text="Server stopped")
        self.stats_label.config(text="")
    
    def _quit(self):
        self._stop_server()
        self.root.quit()
    
    def run(self):
        self.root.mainloop()


def main():
    # Check dependencies
    try:
        import av
    except ImportError:
        print("ERROR: PyAV not installed. Run: pip install av")
        sys.exit(1)
    
    if not PYVIRTUALCAM_AVAILABLE:
        print("WARNING: pyvirtualcam not installed. Virtual camera output disabled.")
        print("Install with: pip install pyvirtualcam")
    
    app = PhoneCamGUI()
    app.run()


if __name__ == '__main__':
    main()
