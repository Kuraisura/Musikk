AUDIO SEPARATION MODELS DIRECTORY
===================================

Place your ONNX models in this directory for the audio separation engine.

Required Models:
----------------
1. voc_inst_quant.onnx - For Vocals Only preset (ID: 1)
2. htdemucs_5s_quant.onnx - For Instrumental preset (ID: 2)
3. karaoke_quant.onnx - For Karaoke preset (ID: 3)
4. htdemucs_4s_quant.onnx - For Standard preset (ID: 4)
5. bs_roformer_6s_quant.onnx - For Band, Band+, Orchestral, Electronic, Rhythm, and Studio presets (IDs: 5-10)

Model Sources:
--------------
You can obtain pre-trained ONNX models from the following sources:

1. Hugging Face Model Hub:
   - https://huggingface.co/models?search=onnx+audio+separation

2. Official Demucs Repository:
   - https://github.com/facebookresearch/demucs

3. BS-Roformer Repository:
   - https://github.com/MoonInTheRiver/Demucs

4. Convert from PyTorch:
   - Use ONNX exporter scripts to convert PyTorch models to ONNX format

Model Specifications:
---------------------
- Input: 32-bit float PCM, stereo (2 channels), 44100Hz
- Output: Multiple stems depending on the model
- Format: Quantized ONNX (INT8 or FP16 for best performance on mobile)

Performance Notes:
------------------
- Quantized models (INT8) are recommended for mobile devices
- Model file sizes typically range from 50MB to 200MB
- Larger models provide better quality but require more memory
- Processing time depends on device capabilities and model complexity

Cascading Models:
-----------------
Presets 7-10 (Orchestral, Electronic, Rhythm, Studio) use multi-pass cascading:
1. First pass: Extract vocals using voc_inst_quant.onnx
2. Second pass: Process remaining audio using bs_roformer_6s_quant.onnx
3. Residual calculation: Compute "Other" stem from remaining audio

This approach provides better separation quality for complex audio with many instruments.