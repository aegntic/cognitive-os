// Brik Pro - Chat Panel
import { useState, useRef, useEffect } from 'react'
import { Send, Loader2, Sparkles, MessageSquare } from 'lucide-react'
import type { Tool, ChatMessage } from '@/lib/types'

interface ChatPanelProps {
  tool: Tool | undefined
  onGenerate: (prompt: string) => void
  onIterate: (prompt: string) => void
  isGenerating: boolean
  width: number
}

export function ChatPanel({ tool, onGenerate, onIterate, isGenerating, width }: ChatPanelProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [isExpanded, setIsExpanded] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    if (tool?.chatHistory) {
      setMessages(tool.chatHistory)
    } else {
      setMessages([])
    }
  }, [tool?.chatHistory])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!input.trim() || isGenerating) return

    const prompt = input.trim()
    setInput('')
    
    if (!tool || !tool.code) {
      onGenerate(prompt)
    } else {
      onIterate(prompt)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit(e)
    }
  }

  const autoResize = () => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`
    }
  }

  return (
    <div 
      className="flex flex-col bg-[#111] border-r border-[#222]"
      style={{ width, minWidth: 280, maxWidth: 600 }}
    >
      <div className="flex items-center justify-between px-4 py-3 border-b border-[#222]">
        <div className="flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-[#8b5cf6]" />
          <span className="font-medium text-white">Art Director</span>
          {tool && tool.code && (
            <span className="px-2 py-0.5 text-xs bg-[#8b5cf6]/20 text-[#8b5cf6] rounded">Iterating</span>
          )}
        </div>
        <button
          onClick={() => setIsExpanded(!isExpanded)}
          className="p-1 hover:bg-[#222] rounded transition-colors text-[#888] hover:text-white"
          title={isExpanded ? 'Collapse' : 'Expand'}
        >
          {isExpanded ? '⛶' : '⛶'}
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-4" style={{ minHeight: 0 }}>
        {messages.length === 0 && !tool?.code ? (
          <div className="text-center text-[#666] py-8">
            <MessageSquare className="mx-auto mb-3 w-10 h-10 text-[#333]" />
            <p className="text-sm">Describe your creative vision</p>
            <p className="text-xs mt-1">Brik Pro will generate a living design tool</p>
          </div>
        ) : (
          messages.map((msg) => (
            <div key={msg.id} className={`flex gap-3 ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}>
              <div 
                className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
                  msg.role === 'user' ? 'bg-[#333] text-white' : 'bg-[#8b5cf6] text-black'
                }`}
              >
                {msg.role === 'user' ? '✦' : '◆'}
              </div>
              <div 
                className={`max-w-[80%] ${
                  msg.role === 'user' ? 'text-right' : ''
                }`}
              >
                <div 
                  className={`px-4 py-2 rounded-2xl text-sm ${
                    msg.role === 'user'
                      ? 'bg-[#2a2a2a] text-white rounded-tr-none'
                      : 'bg-[#1a1a2e] text-white rounded-tl-none'
                  }`}
                >
                  {msg.content}
                </div>
                <div className={`text-xs text-[#666] mt-1 px-1 ${msg.role === 'user' ? 'text-right' : ''}`}>
                  {new Date(msg.timestamp).toLocaleTimeString()}
                </div>
              </div>
            </div>
          ))
        )}
        <div ref={messagesEndRef} />
      </div>

      <form onSubmit={handleSubmit} className="p-4 border-t border-[#222] bg-[#0f0f0f]">
        <div className="relative">
          <textarea
            ref={textareaRef}
            value={input}
            onChange={(e) => { setInput(e.target.value); autoResize() }}
            onKeyDown={handleKeyDown}
            placeholder={tool?.code ? 'Refine your tool...' : 'Describe your vision...'}
            disabled={isGenerating}
            rows={1}
            className="w-full bg-[#1a1a1a] border border-[#333] rounded-xl px-4 py-3 text-white placeholder-[#666] focus:outline-none focus:border-[#8b5cf6] resize-none min-h-[48px] max-h-[200px] pr-12"
            style={{ fontSize: '14px', lineHeight: '1.5' }}
          />
          <button
            type="submit"
            disabled={!input.trim() || isGenerating}
            className="absolute right-2 bottom-2 p-2 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed
              bg-[#8b5cf6] text-black hover:bg-[#7c3aed] active:bg-[#6d28d9]"
          >
            {isGenerating ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <Send className="w-5 h-5" />
            )}
          </button>
        </div>
        <p className="text-xs text-[#555] mt-2 text-center">
          {tool?.code ? 'Press Enter to iterate • Shift+Enter for new line' : 'Press Enter to generate • Shift+Enter for new line'}
        </p>
      </form>
    </div>
  )
}