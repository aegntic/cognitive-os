// Brik Pro - Local Storage Layer using IndexedDB
import { openDB, DBSchema, IDBPDatabase } from 'idb'
import type { Tool, Project, VersionEntry, ChatMessage, AppSettings, GalleryTool, ControllerType, Controller } from '@/lib/types'

interface BrikProDB extends DBSchema {
  tools: {
    key: string
    value: Tool
    indexes: { 'by-updated': number; 'by-tag': string }
  }
  projects: {
    key: string
    value: Project
    indexes: { 'by-updated': number }
  }
  versions: {
    key: string
    value: VersionEntry
    indexes: { 'by-tool': string; 'by-timestamp': number }
  }
  chatHistory: {
    key: string
    value: ChatMessage
    indexes: { 'by-tool': string; 'by-timestamp': number }
  }
  settings: {
    key: string
    value: { key: string; value: unknown }
  }
  gallery: {
    key: string
    value: GalleryTool
    indexes: { 'by-tag': string; 'by-updated': number }
  }
}

let dbPromise: Promise<IDBPDatabase<BrikProDB>> | null = null

function getDB() {
  if (!dbPromise) {
    dbPromise = openDB<BrikProDB>('brik-pro', 1, {
      upgrade(db) {
        const toolStore = db.createObjectStore('tools', { keyPath: 'id' })
        toolStore.createIndex('by-updated', 'updatedAt')
        toolStore.createIndex('by-tag', 'tags', { multiEntry: true })

        const projectStore = db.createObjectStore('projects', { keyPath: 'id' })
        projectStore.createIndex('by-updated', 'updatedAt')

        const versionStore = db.createObjectStore('versions', { keyPath: 'id' })
        versionStore.createIndex('by-tool', 'toolId')
        versionStore.createIndex('by-timestamp', 'createdAt')

        const chatStore = db.createObjectStore('chatHistory', { keyPath: 'id' })
        chatStore.createIndex('by-tool', 'toolId')
        chatStore.createIndex('by-timestamp', 'timestamp')

        db.createObjectStore('settings', { keyPath: 'key' })

        const galleryStore = db.createObjectStore('gallery', { keyPath: 'id' })
        galleryStore.createIndex('by-tag', 'tags', { multiEntry: true })
        galleryStore.createIndex('by-updated', 'updatedAt')
      },
    })
  }
  return dbPromise
}

export const storage = {
  async getTool(id: string): Promise<Tool | undefined> {
    const db = await getDB()
    return db.get('tools', id)
  },

  async getAllTools(): Promise<Tool[]> {
    const db = await getDB()
    return db.getAllFromIndex('tools', 'by-updated')
  },

  async saveTool(tool: Tool): Promise<void> {
    const db = await getDB()
    tool.updatedAt = Date.now()
    await db.put('tools', tool)
  },

  async deleteTool(id: string): Promise<void> {
    const db = await getDB()
    await db.delete('tools', id)
  },

  async getProject(id: string): Promise<Project | undefined> {
    const db = await getDB()
    return db.get('projects', id)
  },

  async getAllProjects(): Promise<Project[]> {
    const db = await getDB()
    return db.getAllFromIndex('projects', 'by-updated')
  },

  async saveProject(project: Project): Promise<void> {
    const db = await getDB()
    project.updatedAt = Date.now()
    await db.put('projects', project)
  },

  async createVersion(entry: VersionEntry): Promise<void> {
    const db = await getDB()
    await db.put('versions', entry)
  },

  async getVersions(toolId: string): Promise<VersionEntry[]> {
    const db = await getDB()
    return db.getAllFromIndex('versions', 'by-tool', toolId)
  },

  async getVersion(id: string): Promise<VersionEntry | undefined> {
    const db = await getDB()
    return db.get('versions', id)
  },

  async saveChatMessage(message: ChatMessage): Promise<void> {
    const db = await getDB()
    await db.put('chatHistory', message)
  },

  async getChatHistory(toolId: string): Promise<ChatMessage[]> {
    const db = await getDB()
    const messages = await db.getAllFromIndex('chatHistory', 'by-tool', toolId)
    return messages.sort((a, b) => a.timestamp - b.timestamp)
  },

  async clearChatHistory(toolId: string): Promise<void> {
    const db = await getDB()
    const messages = await db.getAllFromIndex('chatHistory', 'by-tool', toolId)
    const tx = db.transaction('chatHistory', 'readwrite')
    for (const msg of messages) {
      await tx.store.delete(msg.id)
    }
    await tx.done
  },

  async getSetting<T>(key: string): Promise<T | undefined> {
    const db = await getDB()
    const entry = await db.get('settings', key)
    return entry?.value as T | undefined
  },

  async setSetting(key: string, value: unknown): Promise<void> {
    const db = await getDB()
    await db.put('settings', { key, value })
  },

  async getSettings(): Promise<AppSettings> {
    const defaults = await this.getDefaultSettings()
    const db = await getDB()
    const entries = await db.getAll('settings')
    const settings: Partial<AppSettings> = {}
    for (const entry of entries) {
      settings[entry.key as keyof AppSettings] = entry.value as never
    }
    return { ...defaults, ...settings }
  },

  async getDefaultSettings(): Promise<AppSettings> {
    return {
      llmConfig: {
        provider: 'ollama',
        baseUrl: 'http://localhost:11434',
        model: 'llama3.1:8b',
        temperature: 0.7,
        maxTokens: 4096,
      },
      theme: 'dark',
      autoSave: true,
      defaultCanvasWidth: 1920,
      defaultCanvasHeight: 1080,
      exportDefaults: {
        format: 'image',
        imageFormat: 'png',
        imageScale: 2,
        transparentBackground: true,
        videoFormat: 'mp4',
        videoDuration: 5,
        videoFps: 30,
        videoQuality: 'high',
        includeBranding: false,
      },
    }
  },

  async getGalleryTools(): Promise<GalleryTool[]> {
    const db = await getDB()
    return db.getAllFromIndex('gallery', 'by-updated')
  },

  async saveGalleryTool(tool: GalleryTool): Promise<void> {
    const db = await getDB()
    await db.put('gallery', tool)
  },

  async exportProject(projectId: string): Promise<string> {
    const project = await this.getProject(projectId)
    if (!project) throw new Error('Project not found')
    
    const tools = await Promise.all(
      project.tools.map(toolId => this.getTool(toolId))
    )
    
    const versions = await Promise.all(
      project.tools.map(toolId => this.getVersions(toolId))
    )
    
    const chatHistory = await Promise.all(
      project.tools.map(toolId => this.getChatHistory(toolId))
    )

    return JSON.stringify({
      project,
      tools: tools.filter(Boolean),
      versions: versions.flat(),
      chatHistory: chatHistory.flat(),
      exportedAt: Date.now(),
    }, null, 2)
  },

  async importProject(json: string): Promise<Project> {
    const data = JSON.parse(json)
    
    for (const tool of data.tools) {
      await this.saveTool(tool)
    }
    
    for (const version of data.versions) {
      await this.createVersion(version)
    }
    
    for (const message of data.chatHistory) {
      await this.saveChatMessage(message)
    }
    
    const project = data.project
    project.tools = data.tools.map((t: Tool) => t.id)
    await this.saveProject(project)
    
    return project
  },
}

export function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
}

export function createDefaultController(type: ControllerType, label: string, order: number): Controller {
  const base = {
    id: generateId(),
    type,
    label,
    order,
    isVisible: true,
    section: 'Main',
  }

  switch (type) {
    case 'slider':
      return { ...base, value: 0.5, defaultValue: 0.5, min: 0, max: 1, step: 0.01 }
    case 'toggle':
      return { ...base, value: false, defaultValue: false }
    case 'dropdown':
      return { ...base, value: '', defaultValue: '', options: ['Option 1', 'Option 2'] }
    case 'color':
      return { ...base, value: '#ffffff', defaultValue: '#ffffff' }
    case 'text':
      return { ...base, value: '', defaultValue: '', placeholder: 'Enter text...' }
    case 'file':
      return { ...base, value: null, defaultValue: null, accept: 'image/*,video/*' }
    case 'button':
      return { ...base, value: null, defaultValue: null }
    case 'number':
      return { ...base, value: 0, defaultValue: 0, min: 0, max: 100, step: 1 }
    default:
      return { ...base, value: '', defaultValue: '' }
  }
}