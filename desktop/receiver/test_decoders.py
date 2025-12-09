#!/usr/bin/env python3
"""Test script to check available H264 decoders"""
import av

print("Checking H264 decoder support...")
print("-" * 50)

# Check specifically for NVIDIA
nvidia_decoders = ['h264_cuvid', 'h264_nvdec']
for dec in nvidia_decoders:
    try:
        codec = av.codec.Codec(dec, 'r')
        print(f"✓ {dec} - NVIDIA decoder AVAILABLE")
    except Exception as e:
        print(f"✗ {dec} - Not available ({e})")

# Check other HW decoders
other_decoders = ['h264_qsv', 'h264_amf', 'h264_d3d11va', 'h264_dxva2']
for dec in other_decoders:
    try:
        codec = av.codec.Codec(dec, 'r')
        print(f"✓ {dec} - AVAILABLE")
    except Exception as e:
        print(f"✗ {dec} - Not available")

# Check CPU decoder
try:
    codec = av.codec.Codec('h264', 'r')
    print(f"✓ h264 - CPU decoder AVAILABLE")
except Exception as e:
    print(f"✗ h264 - Not available ({e})")

print("-" * 50)
print("\nAll codecs with 'h264' or 'cuvid' or 'nvdec' in name:")
for c in sorted(av.codecs_available):
    if 'h264' in c.lower() or 'cuvid' in c.lower() or 'nvdec' in c.lower():
        print(f"  {c}")
