#!/usr/bin/env python3
"""
Local stand-in for the Sarvam and ElevenLabs APIs, for exercising the full
hold -> recognise -> translate -> speak loop on an emulator with no real keys.

    python3 tools/mock_cloud.py            # listens on :8797
    # local.properties:
    #   itantra.debug.sarvam.url=http://10.0.2.2:8787/
    #   itantra.debug.elevenlabs.url=http://10.0.2.2:8787/

Every response is the real shape the app parses, and TTS returns a genuine
16 kHz RIFF/WAVE tone so playback goes through the real AudioPlayer.
Requests are logged so the wire can be inspected.
"""
import base64, json, math, struct, sys
from http.server import BaseHTTPRequestHandler, HTTPServer

def wav_tone(seconds=0.6, hz=440, rate=16000):
    n = int(seconds * rate)
    pcm = b''.join(struct.pack('<h', int(9000 * math.sin(2*math.pi*hz*i/rate))) for i in range(n))
    hdr = b'RIFF' + struct.pack('<I', 36+len(pcm)) + b'WAVE' + b'fmt ' + struct.pack('<IHHIIHH',16,1,1,rate,rate*2,2,16) + b'data' + struct.pack('<I', len(pcm))
    return hdr + pcm

WAV = wav_tone()

class H(BaseHTTPRequestHandler):
    def _send(self, code, body, ctype='application/json'):
        self.send_response(code); self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body))); self.end_headers(); self.wfile.write(body)

    def _auth(self, header):
        return bool(self.headers.get(header, '').strip())

    def log_message(self, fmt, *a):
        sys.stderr.write("MOCK %s %s\n" % (self.command, self.path))

    def do_GET(self):
        if self.path.startswith('/v2/voices'):
            if not self._auth('xi-api-key'): return self._send(401, b'{"detail":{"message":"Invalid API key"}}')
            return self._send(200, json.dumps({"voices":[{"voice_id":"mock-voice-1","name":"MockVoice"}]}).encode())
        self._send(404, b'{}')

    def do_POST(self):
        n = int(self.headers.get('Content-Length', 0)); body = self.rfile.read(n)
        p = self.path.split('?')[0]
        # ── ElevenLabs ──
        if p == '/v1/speech-to-text':
            if not self._auth('xi-api-key'): return self._send(401, b'{"detail":{"message":"Invalid API key"}}')
            sys.stderr.write("MOCK   scribe got %d bytes multipart\n" % n)
            return self._send(200, json.dumps({"text":"मुझे पानी चाहिए","language_code":"hi","language_probability":0.97}).encode())
        if p.startswith('/v1/text-to-speech/'):
            if not self._auth('xi-api-key'): return self._send(401, b'{"detail":{"message":"Invalid API key"}}')
            req = json.loads(body or b'{}')
            sys.stderr.write("MOCK   eleven tts model=%s lang=%s text=%r\n" % (req.get('model_id'), req.get('language_code'), req.get('text')))
            return self._send(200, WAV, 'audio/wav')
        # ── Sarvam ──
        if p == '/speech-to-text':
            if not self._auth('api-subscription-key'): return self._send(403, b'{"error":{"message":"Invalid or missing authentication credentials"}}')
            return self._send(200, json.dumps({"transcript":"मुझे पानी चाहिए","language_code":"hi-IN"}).encode())
        if p == '/translate':
            if not self._auth('api-subscription-key'): return self._send(403, b'{"error":{"message":"Invalid or missing authentication credentials"}}')
            req = json.loads(body or b'{}')
            sys.stderr.write("MOCK   sarvam translate %s->%s %r\n" % (req.get('source_language_code'), req.get('target_language_code'), req.get('input')))
            return self._send(200, json.dumps({"translated_text":"I need water"}).encode())
        if p == '/text-to-speech':
            if not self._auth('api-subscription-key'): return self._send(403, b'{"error":{"message":"Invalid or missing authentication credentials"}}')
            return self._send(200, json.dumps({"audios":[base64.b64encode(WAV).decode()]}).encode())
        self._send(404, b'{}')

if __name__ == '__main__':
    print("mock cloud on :8797", flush=True)
    HTTPServer(('0.0.0.0', 8797), H).serve_forever()
