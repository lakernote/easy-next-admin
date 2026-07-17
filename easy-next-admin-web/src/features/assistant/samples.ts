export interface AssistantSample {
  message: string
  expected: string
  session: 'NEW' | 'CURRENT'
}

export interface AssistantSampleGroup {
  key: string
  title: string
  description: string
  samples: readonly AssistantSample[]
}

export const DEFAULT_ASSISTANT_SAMPLE = '2+2等于多少？'

export const assistantSampleGroups: readonly AssistantSampleGroup[] = [
  {
    key: 'direct-replies',
    title: '基础直答',
    description: '验证无需 Tool 的一次模型回复。',
    samples: [
      { message: DEFAULT_ASSISTANT_SAMPLE, expected: '应一次直接回答 4，不调用 Tool。', session: 'NEW' }
    ]
  },
  {
    key: 'read-only-tools',
    title: '只读 Tool',
    description: '验证真实查询和依赖调用。',
    samples: [
      { message: '我有哪些待办', expected: '应选择待办查询 Tool，数量和单号来自真实数据。', session: 'NEW' },
      { message: '帮我查一下请假流程状态', expected: '应选择流程查询 Tool，不回答请假制度。', session: 'NEW' },
      { message: '查我最近一个流程的具体进度', expected: '应通过一次流程列表查询直接回答。', session: 'NEW' },
      { message: '查一下我最近发起的一笔申请单号', expected: '应选择流程查询 Tool，并返回业务单号或明确说明没有记录。', session: 'NEW' }
    ]
  },
  {
    key: 'leave-actions',
    title: '请假动作',
    description: '修改、确认、取消需要先在同一会话创建草稿。',
    samples: [
      { message: '我想今天下午请假', expected: '应保存已知时间，并追问请假类型和原因。', session: 'NEW' },
      { message: '我想今天下午请病假，原因是身体不舒服', expected: '应生成完整草稿，不能直接提交。', session: 'NEW' },
      { message: '帮我补一张上周五 13:30 到 18:00 的病假，原因是身体不舒服', expected: '应解析相对日期并生成待确认草稿。', session: 'NEW' },
      { message: '请假原因改成陪家人看病', expected: '应修改当前草稿，保留其他已确认字段。', session: 'CURRENT' },
      { message: '确认提交', expected: '仅在完整草稿存在时执行真实写入。', session: 'CURRENT' },
      { message: '刚刚的申请单号是多少？', expected: '应从同一会话的 Last Result 结构化引用回答，不能猜单号。', session: 'CURRENT' },
      { message: '核对刚刚提交的请假单，告诉我申请人、请假时间、原因和当前状态', expected: '应使用上一轮单号调用请假单详情 Tool。', session: 'CURRENT' },
      { message: '算了，取消吧', expected: '应取消当前待处理草稿，不执行业务写入。', session: 'CURRENT' }
    ]
  },
  {
    key: 'workflow-approval',
    title: '流程审批',
    description: '在同一会话依次测试同意，再刷新待办后测试驳回；两种动作都必须二次确认。',
    samples: [
      { message: '先查一下我有哪些待办', expected: '建立带稳定 taskId 的待办引用。', session: 'NEW' },
      { message: '帮我看看前两个待办的流程详情', expected: '应按上一轮引用依次查询两个流程详情。', session: 'CURRENT' },
      { message: '审批第一个', expected: '应生成同意草稿，不得立即审批。', session: 'CURRENT' },
      { message: '确认审批', expected: '应执行已保存的同意动作快照。', session: 'CURRENT' },
      { message: '重新查一下我现在的待办', expected: '应重新读取数据库，不复用旧数量。', session: 'CURRENT' },
      { message: '驳回第一个', expected: '应选择独立驳回 Tool 并生成草稿。', session: 'CURRENT' },
      { message: '确认驳回', expected: '应执行已保存的驳回动作快照。', session: 'CURRENT' }
    ]
  },
  {
    key: 'knowledge-boundaries',
    title: '企业知识边界',
    description: '验证无真实数据源时不编造企业事实或实时信息。',
    samples: [
      { message: '年假和调休有什么区别', expected: '未接入制度库时应明确能力边界，不编造规则。', session: 'NEW' },
      { message: '我们公司的年假能累计到明年吗？具体有效期多久？', expected: '不得给出未经企业知识源支持的有效期。', session: 'NEW' },
      { message: '今天是星期几啊，今天天气怎么样啊', expected: '可回答上下文日期；没有天气 Tool 时不得编造天气。', session: 'NEW' }
    ]
  },
  {
    key: 'compound-reasoning',
    title: '复合推理',
    description: '验证多个子问题分别选择 Tool、消费 observation 和检查事实来源。',
    samples: [
      {
        message: '年假和调休有什么区别？顺便查一下我现在有几个待办。',
        expected: '保留真实待办数量，同时拒绝编造企业制度。',
        session: 'NEW'
      },
      {
        message: '我有哪些待办？另外告诉我 2+3 等于多少。',
        expected: '一轮查询真实待办，并直接回答简单算术。',
        session: 'NEW'
      },
      {
        message: '先查我最近一个流程，再告诉我它的具体进度。',
        expected: '完成列表到详情的依赖 Tool 链，不靠字符串拼接。',
        session: 'NEW'
      }
    ]
  }
]
