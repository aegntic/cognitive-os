// Brik Pro - LLM Integration for Tool Generation
import type { LLMConfig, Controller, ChatMessage } from '@/lib/types'
import { createDefaultController } from '@/lib/storage'

const SYSTEM_PROMPT = `You are Brik Pro's Creative Coder agent. You generate complete, working creative coding tools as single-file TypeScript/JavaScript modules.

TARGET PLATFORM: Browser canvas (Canvas 2D or WebGL via raw WebGL or Three.js if needed)
OUTPUT FORMAT: A single TypeScript module exporting a Tool class

REQUIREMENTS:
1. The Tool class MUST have:
   - constructor(canvas: HTMLCanvasElement, controllers: Controller[])
   - init(): Promise<void> - setup shaders, textures, audio, etc.
   - render(timestamp: number): void - main render loop
   - resize(width: number, height: number): void
   - destroy(): void - cleanup
   - updateController(id: string, value: unknown): void

2. Controllers are passed in and mapped by ID. Use them to drive parameters.

3. Code must be self-contained, no external imports except standard Web APIs.

4. Include GLSL shaders as template strings if using WebGL.

5. Handle all errors gracefully.

6. Animation should use requestAnimationFrame internally.

7. Support pointer interaction (mouse/touch) if described.

8. Support audio input if described (getUserMedia + AudioContext).

CONTROLLER TYPES: slider, toggle, dropdown, color, text, file, button, number

EXAMPLE MINIMAL TOOL:
\`\`\`typescript
export class Tool {
  private canvas: HTMLCanvasElement
  private ctx: CanvasRenderingContext2D
  private controllers: Map<string, Controller>
  private time = 0
  
  constructor(canvas: HTMLCanvasElement, controllers: Controller[]) {
    this.canvas = canvas
    this.ctx = canvas.getContext('2d')!
    this.controllers = new Map(controllers.map(c => [c.id, c]))
  }
  
  async init() {}
  
  render(timestamp: number) {
    this.time = timestamp * 0.001
    const { width, height } = this.canvas
    this.ctx.clearRect(0, 0, width, height)
    // ... draw using this.controllers.get('id')?.value
    requestAnimationFrame(this.render.bind(this))
  }
  
  resize(w: number, h: number) {
    this.canvas.width = w
    this.canvas.height = h
  }
  
  destroy() {}
  
  updateController(id: string, value: unknown) {
    const ctrl = this.controllers.get(id)
    if (ctrl) ctrl.value = value
  }
}
\`\`\`

Generate ONLY the Tool class code. No markdown, no explanation.` 

export class LLMEngine {
  private config: LLMConfig
  private abortController: AbortController | null = null

  constructor(config: LLMConfig) {
    this.config = config
  }

  updateConfig(config: LLMConfig) {
    this.config = config
  }

  async generateTool(prompt: string, existingCode?: string, controllers?: Controller[]): Promise<{ code: string; controllers: Controller[]; description: string }> {
    const messages = this.buildMessages(prompt, existingCode, controllers)
    const response = await this.chat(messages, true)
    return this.parseResponse(response)
  }

  async iterateTool(prompt: string, currentCode: string, currentControllers: Controller[], chatHistory: ChatMessage[]): Promise<{ code: string; controllers: Controller[]; description: string }> {
    const messages = this.buildIterationMessages(prompt, currentCode, currentControllers, chatHistory)
    const response = await this.chat(messages, true)
    return this.parseResponse(response)
  }

  async chat(messages: { role: string; content: string }[], stream = false): Promise<string> {
    this.abortController = new AbortController()
    
    const provider = this.config.provider
    let url = ''
    let body: Record<string, unknown> = {
      messages,
      model: this.config.model,
      temperature: this.config.temperature,
      max_tokens: this.config.maxTokens,
      stream,
    }

    switch (provider) {
      case 'ollama':
        url = `${this.config.baseUrl}/api/chat`
        body = { ...body, stream }
        break
      case 'lmstudio':
        url = `${this.config.baseUrl}/v1/chat/completions`
        break
      case 'openai':
        url = `${this.config.baseUrl}/chat/completions`
        body = { ...body, model: this.config.model }
        break
      case 'anthropic':
        url = `${this.config.baseUrl}/v1/messages`
        body = {
          model: this.config.model,
          max_tokens: this.config.maxTokens,
          temperature: this.config.temperature,
          messages: messages.filter(m => m.role !== 'system'),
          system: messages.find(m => m.role === 'system')?.content,
        }
        break
      case 'custom':
        url = `${this.config.baseUrl}/chat/completions`
        break
    }

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    }

    if (this.config.apiKey && provider !== 'ollama') {
      headers['Authorization'] = `Bearer ${this.config.apiKey}`
    }

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
      signal: this.abortController.signal,
    })

    if (!response.ok) {
      const error = await response.text()
      throw new Error(`LLM request failed: ${response.status} - ${error}`)
    }

    if (stream) {
      return this.handleStream(response)
    }

    const data = await response.json()
    
    if (provider === 'anthropic') {
      return data.content[0]?.text || ''
    }
    
    return data.choices?.[0]?.message?.content || data.message?.content || ''
  }

  private async handleStream(response: Response): Promise<string> {
    const reader = response.body?.getReader()
    if (!reader) throw new Error('No response body')
    
    const decoder = new TextDecoder()
    let fullText = ''
    
    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        
        const chunk = decoder.decode(value, { stream: true })
        fullText += chunk
      }
    } finally {
      reader.releaseLock()
    }
    
    return fullText
  }

  abort() {
    this.abortController?.abort()
  }

  private buildMessages(prompt: string, existingCode?: string, controllers?: Controller[]): { role: string; content: string }[] {
    const messages = [
      { role: 'system', content: SYSTEM_PROMPT },
    ]

    if (existingCode) {
      messages.push({
        role: 'user',
        content: `Current tool code:\n\`\`\`typescript\n${existingCode}\n\`\`\`\n\nCurrent controllers:\n${JSON.stringify(controllers, null, 2)}\n\nModify the tool: ${prompt}`,
      })
    } else {
      messages.push({
        role: 'user',
        content: `Create a new tool: ${prompt}\n\nReturn ONLY the Tool class code and a JSON block with controllers and description.`,
      })
    }

    return messages
  }

  private buildIterationMessages(prompt: string, currentCode: string, currentControllers: Controller[], history: ChatMessage[]): { role: string; content: string }[] {
    const messages = [
      { role: 'system', content: SYSTEM_PROMPT },
    ]

    for (const msg of history.slice(-10)) {
      messages.push({ role: msg.role, content: msg.content })
    }

    messages.push({
      role: 'user',
      content: `Current tool code:\n\`\`\`typescript\n${currentCode}\n\`\`\`\n\nCurrent controllers:\n${JSON.stringify(currentControllers, null, 2)}\n\nIterate: ${prompt}`,
    })

    return messages
  }

  private parseResponse(response: string): { code: string; controllers: Controller[]; description: string } {
    // Extract code block
    const codeMatch = response.match(/```(?:typescript|ts|javascript|js)?\n([\s\S]*?)```/)
    const code = codeMatch ? codeMatch[1].trim() : response.trim()

    // Extract JSON block for controllers
    const jsonMatch = response.match(/```json\n([\s\S]*?)```/) || response.match(/\{[\s\S]*\}/)
    let controllers: Controller[] = []
    let description = ''

    if (jsonMatch) {
      try {
        const parsed = JSON.parse(jsonMatch[1] || jsonMatch[0])
        controllers = parsed.controllers || []
        description = parsed.description || ''
      } catch {
        // fallback: extract controllers from code
        controllers = this.extractControllersFromCode(code)
      }
    } else {
      controllers = this.extractControllersFromCode(code)
    }

    // Ensure all controllers have IDs
    controllers = controllers.map((c, i) => ({
      ...c,
      id: c.id || `ctrl-${i}`,
      order: c.order ?? i,
      isVisible: c.isVisible ?? true,
      section: c.section || 'Main',
    }))

    return { code, controllers, description }
  }

  private extractControllersFromCode(code: string): Controller[] {
    const controllers: Controller[] = []
    const controllerMatches = code.matchAll(/this\.controllers\.get\(['"]([^'"]+)['"]\)\?\.\s*value/gi)
    
    for (const match of controllerMatches) {
      const id = match[1]
      if (!controllers.find(c => c.id === id)) {
        controllers.push(createDefaultController('slider', id, controllers.length))
      }
    }

    return controllers
  }
}

export function createLLMEngine(config: LLMConfig): LLMEngine {
  return new LLMEngine(config)
}