<template>
  <div class="dashboard-container">
    <div class="container">
      <div class="ai-header">
        <div class="ai-title">食刻 · AI 点单助手</div>
        <div class="ai-desc">用一句话描述想吃什么，我来推荐菜品并帮你下单</div>
      </div>

      <div class="ai-notice">
        当前为界面预览：后端 Agent 服务（Python + LangGraph）尚未接入，以下回复为本地占位文案。
      </div>

      <div ref="chatBody"
           class="ai-body">
        <div v-for="(m, i) in messages"
             :key="i"
             :class="['ai-msg', m.role === 'me' ? 'ai-msg-me' : 'ai-msg-ai']">
          {{ m.text }}
        </div>
      </div>

      <div class="ai-footer">
        <el-input v-model="input"
                  placeholder="想吃辣的、30 块以内……"
                  @keyup.enter.native="send" />
        <el-button type="primary"
                   :loading="sending"
                   class="ai-send"
                   @click="send">
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'

interface ChatMessage {
  role: string
  text: string
}

@Component({
  name: 'AiOrder'
})
export default class extends Vue {
  private input: string = ''
  private sending: boolean = false
  private cursor: number = 0
  private messages: ChatMessage[] = [
    {
      role: 'ai',
      text:
        '你好，我是食刻的 AI 点单助手。\n\n接入后会支持这样的问法：「想吃辣的，30 块以内」。'
    }
  ]
  private placeholders: string[] = [
    '（占位回复）我还没接上真实模型。接入后会调用菜品查询工具，再按你的口味和预算给建议。',
    '（占位回复）后端 Agent 服务上线后，我会查询真实菜品数据再回答你。',
    '（占位回复）这一条是本地写死的，用来先看界面效果。'
  ]

  $refs!: {
    chatBody: any
  }

  private send() {
    const text = this.input.trim()
    if (!text || this.sending) {
      return
    }
    this.messages.push({ role: 'me', text })
    this.input = ''
    this.sending = true
    this.$nextTick(() => this.scrollToBottom())
    // TODO 接入后端：POST /ai/chat → Java 转发 → Python Agent（LangGraph）
    setTimeout(() => {
      this.messages.push({
        role: 'ai',
        text: this.placeholders[this.cursor++ % this.placeholders.length]
      })
      this.sending = false
      this.$nextTick(() => this.scrollToBottom())
    }, 700)
  }

  private scrollToBottom() {
    const el = this.$refs.chatBody
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  }
}
</script>

<style lang="scss" scoped>
.dashboard {
  &-container {
    margin: 30px;

    .container {
      background: #fff;
      padding: 24px 28px 28px;
      border-radius: 4px;
    }
  }
}

.ai-header {
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;

  .ai-title {
    font-size: 18px;
    font-weight: 600;
    color: #333;
  }

  .ai-desc {
    margin-top: 6px;
    font-size: 13px;
    color: #909399;
  }
}

.ai-notice {
  margin-top: 16px;
  padding: 10px 14px;
  background: #fdf6ec;
  color: #b88230;
  font-size: 13px;
  border-radius: 4px;
}

.ai-body {
  height: 380px;
  margin-top: 16px;
  padding: 4px;
  overflow-y: auto;
}

.ai-msg {
  max-width: 76%;
  margin-bottom: 12px;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  border-radius: 8px;
}

.ai-msg-ai {
  background: #f4f4f5;
  color: #303133;
}

.ai-msg-me {
  margin-left: auto;
  background: #fff3cd;
  color: #333;
}

.ai-footer {
  display: flex;
  align-items: center;
  margin-top: 16px;

  .el-input {
    flex: 1;
  }

  .ai-send {
    margin-left: 12px;
  }
}
</style>
