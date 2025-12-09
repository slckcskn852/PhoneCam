# GPU Decoding Implementation Notes

## Status: Not Used (CPU Decoding Preferred)

After testing multiple GPU decoding approaches, CPU decoding with PyAV was found to provide the best latency for this real-time streaming application.

## Approaches Tested

### 1. PyNvVideoCodec (NVIDIA's Python NVDEC wrapper)
- **Issue**: Designed for file-based decoding, not suitable for real-time NAL unit streaming
- **Issue**: Required CUDA 12 runtime DLLs (cudart64_12.dll) which weren't present with CUDA 13 installation
- **Result**: Not used

### 2. FFmpeg Subprocess with h264_cuvid
- **Approach**: Piped H.264 data through `ffmpeg.exe` using the `h264_cuvid` NVDEC decoder
- **Issue**: Subprocess piping introduced significant latency (~200-500ms)
- **Issue**: Process startup overhead and buffer delays
- **Result**: Latency too high for real-time use

### 3. PyAV with h264_cuvid codec
- **Approach**: Direct PyAV codec access to NVDEC
- **Issue**: PyAV's codec context setup doesn't work well with streaming NAL units
- **Issue**: Requires CUDA context management that conflicts with main thread
- **Result**: Unstable, not reliable

## Current Solution: PyAV CPU Decoding

The current implementation uses PyAV's CPU decoder with optimizations:

```python
codec = av.codec.CodecContext.create('h264', 'r')
codec.options = {
    'threads': '8',        # Multi-threaded decoding
    'flags': 'low_delay',  # Minimize buffering
    'flags2': 'fast',      # Speed over quality
}
```

### Performance
- **CPU Usage**: 10-15% on modern multi-core CPUs
- **Latency**: 80-120ms glass-to-glass
- **Compatibility**: Works on any system without GPU requirements

### Why CPU is Acceptable
1. H.264 decoding is very efficient on modern CPUs
2. 8-thread decoding distributes load across cores
3. Low-delay flags minimize frame buffering
4. No GPU context switching overhead
5. Simpler, more reliable implementation

## Future GPU Decoding Options

If GPU decoding is needed in the future, consider:

1. **Native Python CUDA/NVDEC bindings** (custom implementation)
2. **GStreamer with NVDEC plugins** (more complex but proven)
3. **DirectShow/Media Foundation** (Windows-native, complex)
4. **Vulkan Video Decode** (modern, cross-platform)

These would require significant development effort and may not provide meaningful latency improvements for this use case.
