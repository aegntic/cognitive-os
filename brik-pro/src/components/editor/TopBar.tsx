// Brik Pro - Top Bar
import { useState } from 'react'
import { Plus, Download, Code, ExternalLink, Settings, ChevronDown, MoreHorizontal, Trash2, Copy, Image, Video, GitBranch } from 'lucide-react'
import type { Tool, ExportOptions } from '@/lib/types'
import { storage, generateId } from '@/lib/storage'
import { cn } from '@/lib/utils'

interface TopBarProps {
  tools: Tool[]
  activeToolId: string | null
  onToolSelect: (id: string) => void
  onNewTool: () => void
  onExport: (format: 'image' | 'video' | 'code' | 'embed', options?: ExportOptions) => void
  onShowHistory?: () => void
}

export function TopBar({ tools, activeToolId, onToolSelect, onNewTool, onExport, onShowHistory }: TopBarProps) {
  const [showExportMenu, setShowExportMenu] = useState(false)
  const [showSettings, setShowSettings] = useState(false)
  const [showToolMenu, setShowToolMenu] = useState<string | null>(null)
  const activeTool = tools.find(t => t.id === activeToolId)

  const handleExport = async (format: 'image' | 'video' | 'code' | 'embed') => {
    if (!activeTool) return
    setShowExportMenu(false)
    const defaults: ExportOptions = {
      format,
      imageFormat: 'png',
      imageScale: 2,
      transparentBackground: true,
      videoFormat: 'mp4',
      videoDuration: 5,
      videoFps: 30,
      videoQuality: 'high',
      includeBranding: false,
    }
    onExport(format, defaults)
  }

  const handleDuplicateTool = async () => {
    if (!activeTool) return
    const newTool: Tool = {
      ...activeTool,
      id: generateId(),
      name: `${activeTool.name} Copy`,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      version: 1,
    }
    await storage.saveTool(newTool)
    // Tools list will be refreshed via parent
  }

  const handleDeleteTool = async () => {
    if (!activeTool || !confirm(`Delete "${activeTool.name}"?`)) return
    await storage.deleteTool(activeTool.id)
    // Tools list will be refreshed via parent
  }

  return (
    <header className="h-12 bg-[#0d0d0d] border-b border-[#222] flex items-center justify-between px-4">
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 bg-gradient-to-br from-[#8b5cf6] to-[#ec4899] rounded flex items-center justify-center">
            <span className="text-black font-bold text-sm">◆</span>
          </div>
          <span className="font-semibold text-white text-lg">Brik Pro</span>
        </div>

        <div className="hidden md:flex items-center gap-1 bg-[#1a1a1a] rounded-lg p-1 border border-[#222]">
          {tools.map(tool => (
            <button
              key={tool.id}
              onClick={() => onToolSelect(tool.id)}
              className={cn(
                'px-3 py-1.5 text-sm rounded transition-colors flex items-center gap-2',
                tool.id === activeToolId
                  ? 'bg-[#8b5cf6]/20 text-[#8b5cf6] border border-[#8b5cf6]/30'
                  : 'text-[#888] hover:text-white hover:bg-[#222]'
              )}
              title={tool.name}
            >
              <span className="truncate max-w-[150px]">{tool.name}</span>
              <span className="text-xs text-[#666]">v{tool.version}</span>
              <button
                onClick={(e) => {
                  e.stopPropagation()
                  setShowToolMenu(tool.id === showToolMenu ? null : tool.id)
                }}
                className="p-1 hover:bg-[#333] rounded text-[#666] hover:text-white"
              >
                <MoreHorizontal className="w-3 h-3" />
              </button>
            </button>
          ))}
          {showToolMenu && (
            <div className="absolute top-full right-0 mt-1 bg-[#1a1a1a] border border-[#333] rounded-lg shadow-lg py-1 z-50 min-w-[160px]">
              <button
                onClick={() => handleDuplicateTool()}
                className="w-full px-3 py-1.5 text-sm text-white hover:bg-[#222] flex items-center gap-2"
              >
                <Copy className="w-4 h-4" /> Duplicate
              </button>
              <button
                onClick={() => handleDeleteTool()}
                className="w-full px-3 py-1.5 text-sm text-red-400 hover:bg-[#222] flex items-center gap-2"
              >
                <Trash2 className="w-4 h-4" /> Delete
              </button>
            </div>
          )}
        </div>
      </div>

      <div className="flex items-center gap-2">
        <button
          onClick={onNewTool}
          className="px-3 py-1.5 bg-[#8b5cf6] text-black text-sm font-medium rounded-lg hover:bg-[#7c3aed] transition-colors flex items-center gap-2"
        >
          <Plus className="w-4 h-4" />
          <span>New Tool</span>
        </button>

        {activeTool && (
          <div className="relative">
            <button
              onClick={() => setShowExportMenu(!showExportMenu)}
              className="px-3 py-1.5 bg-[#1a1a1a] border border-[#333] text-white text-sm rounded-lg hover:bg-[#222] transition-colors flex items-center gap-2"
            >
              <Download className="w-4 h-4" />
              <span>Export</span>
              <ChevronDown className="w-3 h-3" />
            </button>
            {showExportMenu && (
              <div className="absolute top-full right-0 mt-1 bg-[#1a1a1a] border border-[#333] rounded-lg shadow-lg py-1 z-50 min-w-[180px]">
                <button onClick={() => handleExport('image')} className="w-full px-3 py-2 text-sm text-white hover:bg-[#222] flex items-center gap-2">
                  <Image className="w-4 h-4" /> Image (PNG/JPG)
                </button>
                <button onClick={() => handleExport('video')} className="w-full px-3 py-2 text-sm text-white hover:bg-[#222] flex items-center gap-2">
                  <Video className="w-4 h-4" /> Video (MP4/WebM)
                </button>
                <button onClick={() => handleExport('code')} className="w-full px-3 py-2 text-sm text-white hover:bg-[#222] flex items-center gap-2">
                  <Code className="w-4 h-4" /> Code (HTML)
                </button>
                <button onClick={() => handleExport('embed')} className="w-full px-3 py-2 text-sm text-white hover:bg-[#222] flex items-center gap-2">
                  <ExternalLink className="w-4 h-4" /> Embed (iframe)
                </button>
              </div>
            )}
          </div>
        )}

        {activeTool && onShowHistory && (
          <button
            onClick={onShowHistory}
            className="px-3 py-1.5 bg-[#1a1a1a] border border-[#333] text-white text-sm rounded-lg hover:bg-[#222] transition-colors flex items-center gap-2"
            title="View git-style version history"
          >
            <GitBranch className="w-4 h-4" />
            <span>History</span>
          </button>
        )}

        <button
          onClick={() => setShowSettings(!showSettings)}
          className="p-2 bg-[#1a1a1a] border border-[#333] text-[#888] rounded-lg hover:bg-[#222] hover:text-white transition-colors"
        >
          <Settings className="w-4 h-4" />
        </button>
      </div>
    </header>
  )
}