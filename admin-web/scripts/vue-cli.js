/**
 * 跨平台启动 vue-cli-service 的小包装。
 *
 * 为什么需要它：
 * 本机 Node 22 自带 OpenSSL 3，而项目用的是 Vue CLI 3 / webpack 4，两者不兼容，
 * 不加 --openssl-legacy-provider 会报 "digital envelope routines::unsupported"。
 * 直接在 package.json 里写 "NODE_OPTIONS=xxx vue-cli-service serve" 在 Windows 的
 * cmd.exe 下不生效，所以用这个脚本给子进程注入环境变量。
 *
 * 注意：这里用 node 直接跑 vue-cli-service 的入口 js，
 * 不走 npx（Node 22 在 Windows 上 spawn .cmd 会报 EINVAL）。
 *
 * 用法：node scripts/vue-cli.js serve
 *       node scripts/vue-cli.js build
 */
const { spawn } = require('child_process')

const cliBin = require.resolve('@vue/cli-service/bin/vue-cli-service.js')
const args = process.argv.slice(2)

const nodeOptions = [process.env.NODE_OPTIONS, '--openssl-legacy-provider']
  .filter(Boolean)
  .join(' ')

const child = spawn(process.execPath, [cliBin, ...args], {
  stdio: 'inherit',
  env: { ...process.env, NODE_OPTIONS: nodeOptions }
})

child.on('exit', (code) => process.exit(code === null ? 1 : code))
