/**
 * 浏览器录音工具：MediaRecorder 录音 → 解码重采样为 16kHz 单声道 → 编码成 WAV Blob。
 *
 * 为什么自己转 WAV：
 *  · ASR 服务（硅基流动 SenseVoice 等）主要接受 wav/mp3，浏览器 MediaRecorder 默认产出
 *    audio/webm;codecs=opus，直接上传大概率被拒；
 *  · 用 OfflineAudioContext 重采样到 16kHz 单声道 16bit PCM，30 秒录音只有约 1MB，
 *    体积小、兼容性稳，任何主流转写服务都能吃。
 *
 * 用法：
 *   const rec = await startWavRecorder()   // 内部会请求麦克风权限
 *   const wavBlob = await rec.stop()       // 停止并拿到 WAV Blob（自动释放麦克风）
 *   rec.cancel()                           // 放弃本次录音（释放麦克风，不产出文件）
 */

const TARGET_SAMPLE_RATE = 16000

/** 启动录音。失败（用户拒绝权限/无麦克风）抛出原始错误，由调用方提示 */
export async function startWavRecorder() {
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
  const recorder = new MediaRecorder(stream)
  const chunks = []
  recorder.ondataavailable = (e) => { if (e.data && e.data.size > 0) chunks.push(e.data) }
  recorder.start()

  const release = () => stream.getTracks().forEach((t) => t.stop())

  return {
    /** 停止录音并返回 WAV Blob；空录音（没有采到任何数据）返回 null */
    stop: () =>
      new Promise((resolve, reject) => {
        recorder.onstop = async () => {
          release()
          try {
            if (!chunks.length) { resolve(null); return }
            const webm = new Blob(chunks, { type: recorder.mimeType || 'audio/webm' })
            resolve(await toWav(webm))
          } catch (e) {
            reject(e)
          }
        }
        recorder.stop()
      }),
    /** 放弃本次录音（释放麦克风，不产出文件） */
    cancel: () => {
      // onstop 已被 stop 流程占用时也不冲突：release 幂等
      recorder.onstop = null
      try { recorder.stop() } catch { /* 已停止时 ignore */ }
      release()
    },
  }
}

/** 任意浏览器可解码的音频 Blob → 16kHz 单声道 16bit PCM WAV Blob */
async function toWav(blob) {
  const arrayBuffer = await blob.arrayBuffer()
  const ctx = new (window.AudioContext || window.webkitAudioContext)()
  const decoded = await ctx.decodeAudioData(arrayBuffer)
  await ctx.close()

  // 用 OfflineAudioContext 做降采样 + 混成单声道（比手写线性插值稳）
  const frames = Math.max(1, Math.ceil(decoded.duration * TARGET_SAMPLE_RATE))
  const off = new OfflineAudioContext(1, frames, TARGET_SAMPLE_RATE)
  const src = off.createBufferSource()
  src.buffer = decoded
  src.connect(off.destination)
  src.start()
  const rendered = await off.startRendering()
  const samples = rendered.getChannelData(0)

  return new Blob([encodeWav(samples, TARGET_SAMPLE_RATE)], { type: 'audio/wav' })
}

/** Float32 采样 → 标准 44 字节头 + 16bit PCM 的 WAV ArrayBuffer */
function encodeWav(samples, sampleRate) {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  const writeStr = (offset, str) => {
    for (let i = 0; i < str.length; i++) view.setUint8(offset + i, str.charCodeAt(i))
  }
  writeStr(0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeStr(8, 'WAVE')
  writeStr(12, 'fmt ')
  view.setUint32(16, 16, true)          // fmt 块长度
  view.setUint16(20, 1, true)           // PCM
  view.setUint16(22, 1, true)           // 单声道
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)  // 字节率 = 采样率 × 2字节
  view.setUint16(32, 2, true)           // 块对齐
  view.setUint16(34, 16, true)          // 位深
  writeStr(36, 'data')
  view.setUint32(40, samples.length * 2, true)
  let offset = 44
  for (let i = 0; i < samples.length; i++, offset += 2) {
    const s = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true)
  }
  return buffer
}
