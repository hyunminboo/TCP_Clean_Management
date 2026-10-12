import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    open: true, // 서버가 켜질 때 기본 웹 브라우저(크롬, 사파리 등)에서 자동으로 열리게 설정
    proxy: {
      '/api/kakao-transit': {
        target: 'https://dapi.kakao.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/kakao-transit/, '/v2/routing/publictraffic'),
        headers: {
          'Authorization': 'KakaoAK 538ae72c5fe3a20e162f6d92fadf4f13',
          'KA': 'sdk/1.0.0 os/javascript lang/ko-KR res/1920x1080 device/desktop origin/http://localhost:5173'
        }
      },
      '/api/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/api/admin': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/api/public': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
