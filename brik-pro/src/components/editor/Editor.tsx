// Brik Pro - Editor Main Component
import { useState, useEffect, useRef } from 'react'
import { CanvasPanel } from './CanvasPanel'
import { ChatPanel } from './ChatPanel'
import { ControllerPanel } from './ControllerPanel'
import { TopBar } from './TopBar'
import { storage, generateId } from '@/lib/storage'
import type { Tool, ExportOptions, VersionEntry } from '@/lib/types'
import { VersionHistory } from './VersionHistory'
import { createLLMEngine } from '@/lib/llm'
import { PanelResizeHandle } from './PanelResizeHandle'
import { handleExport } from '@/lib/export'

export function Editor() {
  const [activeToolId, setActiveToolId] = useState<string | null>(null)
  const [tools, setTools] = useState<Tool[]>([])
  const [leftWidth, setLeftWidth] = useState(380)
  const [rightWidth, setRightWidth] = useState(320)
  const [isGenerating, setIsGenerating] = useState(false)
  const [showHistory, setShowHistory] = useState(false)
  const [versions, setVersions] = useState<VersionEntry[]>([])
  const llmEngineRef = useRef<ReturnType<typeof createLLMEngine> | null>(null)
  const settingsRef = useRef<{ llmConfig: any }>({ llmConfig: null })

  useEffect(() => {
    loadTools()
    loadSettings()
  }, [])

  async function loadTools() {
    const allTools = await storage.getAllTools()
    setTools(allTools)
    if (allTools.length > 0 && !activeToolId) {
      setActiveToolId(allTools[0].id)
    }
  }

  async function loadSettings() {
    const settings = await storage.getSettings()
    settingsRef.current.llmConfig = settings.llmConfig
    llmEngineRef.current = createLLMEngine(settings.llmConfig)
  }

  async function loadVersions(toolId: string) {
    const vers = await storage.getVersions(toolId)
    setVersions(vers)
  }

  useEffect(() => {
    if (activeToolId) {
      loadVersions(activeToolId)
    }
  }, [activeToolId])

  async function createNewTool() {
    const newTool: Tool = {
      id: generateId(),
      name: 'Untitled Tool',
      description: '',
      code: '',
      controllers: [],
      canvasWidth: 1920,
      canvasHeight: 1080,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      version: 1,
      isPublished: false,
      tags: [],
      authorId: 'local',
      chatHistory: [],
    }
    await storage.saveTool(newTool)
    setTools(prev => [newTool, ...prev])
    setActiveToolId(newTool.id)
  }

  async function handleGenerate(prompt: string) {
    if (!llmEngineRef.current || !activeToolId) return
    
    const tool = tools.find(t => t.id === activeToolId)
    if (!tool) return

    setIsGenerating(true)
    try {
      const result = await llmEngineRef.current.generateTool(prompt)
      
      const updatedTool: Tool = {
        ...tool,
        code: result.code,
        controllers: result.controllers,
        description: result.description,
        updatedAt: Date.now(),
        version: tool.version + 1,
        chatHistory: [
          ...tool.chatHistory,
          { id: generateId(), role: 'user' as const, content: prompt, timestamp: Date.now() },
          { id: generateId(), role: 'assistant' as const, content: result.description || 'Tool generated', timestamp: Date.now() },
        ],
      }
      
      await storage.saveTool(updatedTool)
      await storage.createVersion({
        id: generateId(),
        toolId: tool.id,
        version: updatedTool.version,
        code: result.code,
        controllers: result.controllers,
        canvasWidth: tool.canvasWidth,
        canvasHeight: tool.canvasHeight,
        description: result.description,
        createdAt: Date.now(),
        authorId: 'local',
      })
      
      setTools(prev => prev.map(t => t.id === activeToolId ? updatedTool : t))
    } catch (error) {
      console.error('Generation failed:', error)
      const tool = tools.find(t => t.id === activeToolId)
      if (tool) {
        const updatedTool: Tool = {
          ...tool,
          chatHistory: [
            ...tool.chatHistory,
            { id: generateId(), role: 'user' as const, content: prompt, timestamp: Date.now() },
            { id: generateId(), role: 'assistant' as const, content: `Error: ${error instanceof Error ? error.message : 'Unknown error'}`, timestamp: Date.now() },
          ],
        }
        await storage.saveTool(updatedTool)
        setTools(prev => prev.map(t => t.id === activeToolId ? updatedTool : t))
      }
    } finally {
      setIsGenerating(false)
    }
  }

  async function handleIterate(prompt: string) {
    if (!llmEngineRef.current || !activeToolId) return
    
    const tool = tools.find(t => t.id === activeToolId)
    if (!tool) return

    setIsGenerating(true)
    try {
      const result = await llmEngineRef.current.iterateTool(
        prompt,
        tool.code,
        tool.controllers,
        tool.chatHistory
      )
      
      const updatedTool: Tool = {
        ...tool,
        code: result.code,
        controllers: result.controllers,
        description: result.description,
        updatedAt: Date.now(),
        version: tool.version + 1,
        chatHistory: [
          ...tool.chatHistory,
          { id: generateId(), role: 'user' as const, content: prompt, timestamp: Date.now() },
          { id: generateId(), role: 'assistant' as const, content: result.description || 'Tool updated', timestamp: Date.now() },
        ],
      }
      
      await storage.saveTool(updatedTool)
      await storage.createVersion({
        id: generateId(),
        toolId: tool.id,
        version: updatedTool.version,
        code: result.code,
        controllers: result.controllers,
        canvasWidth: tool.canvasWidth,
        canvasHeight: tool.canvasHeight,
        description: result.description,
        createdAt: Date.now(),
        authorId: 'local',
      })
      
      setTools(prev => prev.map(t => t.id === activeToolId ? updatedTool : t))
    } catch (error) {
      console.error('Iteration failed:', error)
      const errorTool = {
        ...tool,
        chatHistory: [
          ...tool.chatHistory,
          { id: generateId(), role: 'user' as const, content: prompt, timestamp: Date.now() },
          { id: generateId(), role: 'assistant' as const, content: `Error: ${error instanceof Error ? error.message : 'Unknown error'}`, timestamp: Date.now() },
        ],
      }
      await storage.saveTool(errorTool)
      setTools(prev => prev.map(t => t.id === activeToolId ? errorTool : t))
    } finally {
      setIsGenerating(false)
    }
  }

  const activeTool = tools.find(t => t.id === activeToolId)

  const handleToolExport = async (format: 'image' | 'video' | 'code' | 'embed', options?: ExportOptions) => {
    if (!activeTool) return
    await handleExport(activeTool, format, options || { format, imageFormat: "png", imageScale: 2, transparentBackground: true, videoFormat: "mp4", videoDuration: 5, videoFps: 30, videoQuality: "high", includeBranding: false })
  }

  const handleShowHistory = () => setShowHistory(true)

  const handleRestore = async (version: VersionEntry) => {
    if (!activeToolId) return
    const tool = tools.find(t => t.id === activeToolId)
    if (!tool) return

    const restoredTool: Tool = {
      ...tool,
      code: version.code,
      controllers: version.controllers,
      canvasWidth: version.canvasWidth,
      canvasHeight: version.canvasHeight,
      version: tool.version + 1,
      updatedAt: Date.now(),
    }

    await storage.saveTool(restoredTool)

    // Create new version entry marking the restore (git parent)
    await storage.createVersion({
      id: generateId(),
      toolId: activeToolId,
      version: restoredTool.version,
      code: restoredTool.code,
      controllers: restoredTool.controllers,
      canvasWidth: restoredTool.canvasWidth,
      canvasHeight: restoredTool.canvasHeight,
      description: `Restored from v${version.version}`,
      createdAt: Date.now(),
      authorId: "local",
      parentVersionId: version.id,
      commitMessage: `Restore v${version.version}`,
    })

    setTools(prev => prev.map(t => t.id === activeToolId ? restoredTool : t))
    await loadVersions(activeToolId)
    setShowHistory(false)
  }

  const handleFork = async (version: VersionEntry) => {
    const newTool: Tool = {
      id: generateId(),
      name: `Fork of ${tools.find(t => t.id === activeToolId)?.name || "Tool"} (v${version.version})`,
      description: `Forked from version ${version.version}`,
      code: version.code,
      controllers: [...version.controllers],
      canvasWidth: version.canvasWidth,
      canvasHeight: version.canvasHeight,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      version: 1,
      chatHistory: [],
      isPublished: false,
      tags: [],
      authorId: "local",
    }
    await storage.saveTool(newTool)

    // Create initial version for the fork
    await storage.createVersion({
      id: generateId(),
      toolId: newTool.id,
      version: 1,
      code: newTool.code,
      controllers: newTool.controllers,
      canvasWidth: newTool.canvasWidth,
      canvasHeight: newTool.canvasHeight,
      description: "Initial fork",
      createdAt: Date.now(),
      authorId: "local",
      parentVersionId: version.id,
      commitMessage: `Fork from v${version.version}`,
    })

    setTools(prev => [...prev, newTool])
    setActiveToolId(newTool.id)
    setShowHistory(false)
  }

  // Real controller value change handler (lifts state + persists)
  async function handleControllerChange(controllerId: string, value: unknown) {
    if (!activeToolId) return
    const tool = tools.find(t => t.id === activeToolId)
    if (!tool) return

    const updatedControllers = tool.controllers.map(c =>
      c.id === controllerId ? { ...c, value } : c
    )
    const updatedTool: Tool = {
      ...tool,
      controllers: updatedControllers,
      updatedAt: Date.now(),
    }
    await storage.saveTool(updatedTool)
    setTools(prev => prev.map(t => t.id === activeToolId ? updatedTool : t))
  }

  return (
    <div className="h-full flex flex-col bg-[#0a0a0a]">
      <TopBar 
        tools={tools}
        activeToolId={activeToolId}
        onToolSelect={setActiveToolId}
        onNewTool={createNewTool}
        onExport={handleToolExport}
        onShowHistory={handleShowHistory}
      />
      
      <div className="flex-1 flex overflow-hidden">
        <ChatPanel
          tool={activeTool}
          onGenerate={handleGenerate}
          onIterate={handleIterate}
          isGenerating={isGenerating}
          width={leftWidth}
        />
        <PanelResizeHandle
          position={leftWidth}
          onDrag={setLeftWidth}
          min={280}
          max={600}
          direction="horizontal"
        />
        
        <CanvasPanel
          tool={activeTool}
          onControllerChange={handleControllerChange}
          className="flex-1 min-w-0"
        />
        
        <PanelResizeHandle
          position={rightWidth}
          onDrag={setRightWidth}
          min={280}
          max={500}
          direction="horizontal"
          reverse
        />
        
        <ControllerPanel
          tool={activeTool}
          onControllerChange={handleControllerChange}
          width={rightWidth}
        />
      </div>

      <VersionHistory
        isOpen={showHistory}
        onClose={() => setShowHistory(false)}
        tool={activeTool}
        versions={versions}
        onRestore={handleRestore}
        onFork={handleFork}
      />
    </div>
  )
}