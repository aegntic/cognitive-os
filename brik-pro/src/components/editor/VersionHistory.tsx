// Brik Pro - Git-style Version History
import { useState } from 'react'
import { X, RotateCcw, GitBranch, Clock, MessageSquare, Download } from 'lucide-react'
import type { VersionEntry, Tool } from '@/lib/types'

interface VersionHistoryProps {
  isOpen: boolean
  onClose: () => void
  tool: Tool | undefined
  versions: VersionEntry[]
  onRestore: (version: VersionEntry) => void
  onFork: (version: VersionEntry) => void
}

export function VersionHistory({ 
  isOpen, 
  onClose, 
  tool, 
  versions, 
  onRestore, 
  onFork 
}: VersionHistoryProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null)

  if (!isOpen || !tool) return null

  const sortedVersions = [...versions].sort((a, b) => b.createdAt - a.createdAt)

  const currentVersion = tool.version

  const formatTime = (ts: number) => {
    const d = new Date(ts)
    return d.toLocaleString([], { 
      month: 'short', 
      day: 'numeric', 
      hour: '2-digit', 
      minute: '2-digit' 
    })
  }

  const handleRestore = (v: VersionEntry) => {
    onRestore(v)
    onClose()
  }

  const handleFork = (v: VersionEntry) => {
    onFork(v)
    onClose()
  }

  const exportVersion = async (v: VersionEntry) => {
    const data = {
      version: v.version,
      code: v.code,
      controllers: v.controllers,
      canvasWidth: v.canvasWidth,
      canvasHeight: v.canvasHeight,
      description: v.description,
      exportedAt: Date.now(),
    }
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${tool.name.replace(/\s+/g, '-')}-v${v.version}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  return (
    <div className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4">
      <div className="bg-[#111] border border-[#333] rounded-xl w-full max-w-3xl max-h-[80vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-[#222]">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-[#8b5cf6]/10 rounded-lg">
              <GitBranch className="w-5 h-5 text-[#8b5cf6]" />
            </div>
            <div>
              <h2 className="text-white font-semibold text-lg">Version History</h2>
              <p className="text-xs text-[#666]">{tool.name} • {versions.length} versions</p>
            </div>
          </div>
          <button 
            onClick={onClose}
            className="p-2 hover:bg-[#222] rounded-lg text-[#888] hover:text-white transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-auto p-4">
          {sortedVersions.length === 0 ? (
            <div className="text-center py-12 text-[#666]">
              <GitBranch className="w-12 h-12 mx-auto mb-4 opacity-50" />
              <p>No version history yet.</p>
              <p className="text-sm mt-1">Versions are created automatically when you generate or iterate with the AI.</p>
            </div>
          ) : (
            <div className="space-y-2">
              {sortedVersions.map((v) => {
                const isCurrent = v.version === currentVersion
                const isSelected = selectedId === v.id

                return (
                  <div
                    key={v.id}
                    onClick={() => setSelectedId(v.id)}
                    className={`border rounded-lg p-4 cursor-pointer transition-all ${
                      isCurrent 
                        ? 'border-[#8b5cf6] bg-[#8b5cf6]/5' 
                        : isSelected 
                          ? 'border-[#444] bg-[#1a1a1a]' 
                          : 'border-[#222] hover:border-[#333] bg-[#0a0a0a]'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <span className={`font-mono text-sm px-2 py-0.5 rounded ${isCurrent ? 'bg-[#8b5cf6] text-black' : 'bg-[#222] text-[#888]'}`}>
                            v{v.version}
                          </span>
                          {isCurrent && <span className="text-xs text-[#8b5cf6]">CURRENT</span>}
                          {v.parentVersionId && (
                            <span className="text-xs text-[#666] flex items-center gap-1">
                              <GitBranch className="w-3 h-3" /> forked
                            </span>
                          )}
                        </div>

                        <div className="text-white font-medium truncate">
                          {v.description || v.commitMessage || 'Snapshot'}
                        </div>

                        <div className="flex items-center gap-3 text-xs text-[#666] mt-2">
                          <span className="flex items-center gap-1">
                            <Clock className="w-3 h-3" />
                            {formatTime(v.createdAt)}
                          </span>
                          {v.commitMessage && (
                            <span className="flex items-center gap-1">
                              <MessageSquare className="w-3 h-3" />
                              {v.commitMessage}
                            </span>
                          )}
                        </div>

                        <div className="text-xs text-[#555] mt-1 font-mono truncate">
                          {v.canvasWidth}×{v.canvasHeight} • {v.controllers.length} controllers
                        </div>
                      </div>

                      <div className="flex flex-col gap-1.5">
                        {!isCurrent && (
                          <>
                            <button
                              onClick={(e) => { e.stopPropagation(); handleRestore(v) }}
                              className="px-3 py-1.5 text-xs bg-[#8b5cf6] hover:bg-[#7c3aed] text-black rounded flex items-center gap-1.5 font-medium"
                              title="Restore this version as current"
                            >
                              <RotateCcw className="w-3.5 h-3.5" />
                              Restore
                            </button>
                            <button
                              onClick={(e) => { e.stopPropagation(); handleFork(v) }}
                              className="px-3 py-1.5 text-xs bg-[#222] hover:bg-[#333] text-white rounded flex items-center gap-1.5"
                              title="Create a new tool forked from this version"
                            >
                              <GitBranch className="w-3.5 h-3.5" />
                              Fork
                            </button>
                          </>
                        )}
                        <button
                          onClick={(e) => { e.stopPropagation(); exportVersion(v) }}
                          className="px-3 py-1.5 text-xs bg-[#1a1a1a] hover:bg-[#222] text-[#888] hover:text-white rounded flex items-center gap-1.5"
                          title="Export this version as JSON"
                        >
                          <Download className="w-3.5 h-3.5" />
                          Export
                        </button>
                      </div>
                    </div>

                    {/* Code preview */}
                    {isSelected && (
                      <div className="mt-3 pt-3 border-t border-[#222]">
                        <pre className="text-xs text-[#aaa] bg-[#0a0a0a] p-3 rounded overflow-auto max-h-32 font-mono whitespace-pre-wrap">
                          {v.code.slice(0, 800)}{v.code.length > 800 ? '...' : ''}
                        </pre>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-[#222] flex items-center justify-between text-xs text-[#666]">
          <div>
            Click a version to preview. Restore loads it into the current tool.
          </div>
          <div className="flex gap-2">
            <button 
              onClick={onClose}
              className="px-4 py-1.5 rounded bg-[#222] hover:bg-[#333] text-white"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
