// Brik Pro - Core Type Definitions

export type ControllerType = 
  | 'slider' 
  | 'toggle' 
  | 'dropdown' 
  | 'color' 
  | 'text' 
  | 'file' 
  | 'button'
  | 'number'

export interface Controller {
  id: string
  type: ControllerType
  label: string
  value: unknown
  defaultValue: unknown
  min?: number
  max?: number
  step?: number
  options?: string[]
  placeholder?: string
  accept?: string
  section?: string
  order: number
  isVisible: boolean
  onChange?: string
}

export interface Tool {
  id: string
  name: string
  description: string
  code: string
  controllers: Controller[]
  canvasWidth: number
  canvasHeight: number
  thumbnail?: string
  createdAt: number
  updatedAt: number
  version: number
  isPublished: boolean
  tags: string[]
  authorId: string
  parentToolId?: string
  chatHistory: ChatMessage[]
}

export interface Project {
  id: string
  name: string
  description: string
  tools: string[]
  createdAt: number
  updatedAt: number
  thumbnail?: string
  tags: string[]
}

export interface VersionEntry {
  id: string
  toolId: string
  version: number
  code: string
  controllers: Controller[]
  canvasWidth: number
  canvasHeight: number
  description: string
  createdAt: number
  authorId: string
  parentVersionId?: string  // for git-style history chain
  commitMessage?: string
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp: number
  toolId?: string
  isGenerating?: boolean
}

export interface ExportOptions {
  format: 'image' | 'video' | 'code' | 'embed'
  imageFormat?: 'png' | 'jpeg'
  imageScale?: number
  transparentBackground?: boolean
  videoFormat?: 'mp4' | 'webm'
  videoDuration?: number
  videoFps?: number
  videoQuality?: 'low' | 'standard' | 'medium' | 'high'
  includeBranding?: boolean
}

export interface LLMConfig {
  provider: 'ollama' | 'lmstudio' | 'openai' | 'anthropic' | 'custom'
  baseUrl: string
  apiKey?: string
  model: string
  temperature: number
  maxTokens: number
}

export interface AppSettings {
  llmConfig: LLMConfig
  theme: 'light' | 'dark' | 'system'
  autoSave: boolean
  defaultCanvasWidth: number
  defaultCanvasHeight: number
  exportDefaults: Partial<ExportOptions>
}

export interface GalleryTool {
  id: string
  name: string
  description: string
  author: string
  authorId: string
  thumbnail: string
  tags: string[]
  downloads: number
  likes: number
  createdAt: number
  updatedAt: number
  toolData: Tool
  isLocal: boolean
  sourceUrl?: string
}