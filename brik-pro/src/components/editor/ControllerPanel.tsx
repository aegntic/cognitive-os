// Brik Pro - Controller Panel
import { useState } from 'react'
import { ChevronDown, ChevronRight, Trash2, Edit2 } from 'lucide-react'
import type { Tool, Controller } from '@/lib/types'
import { storage, createDefaultController } from '@/lib/storage'
interface ControllerPanelProps {
  tool: Tool | undefined
  onControllerChange: (id: string, value: unknown) => void
  width: number
}

export function ControllerPanel({ tool, onControllerChange, width }: ControllerPanelProps) {
  const [expandedSections, setExpandedSections] = useState<Record<string, boolean>>({})
  const [editMode, setEditMode] = useState(false)
  const [editingController, setEditingController] = useState<Controller | null>(null)

  if (!tool) {
    return (
      <div className="bg-[#111] border-l border-[#222] flex flex-col" style={{ width, minWidth: 280, maxWidth: 500 }}>
        <div className="p-4 border-b border-[#222]">
          <h3 className="text-white font-medium">Controllers</h3>
          <p className="text-xs text-[#666] mt-1">Select a tool to see its controllers</p>
        </div>
        <div className="flex-1 flex items-center justify-center text-[#666] text-sm">
          No tool selected
        </div>
      </div>
    )
  }

  const sections = [...new Set(tool.controllers.map(c => c.section || 'Main'))]

  const handleValueChange = async (controller: Controller, value: unknown) => {
    const updatedControllers = tool.controllers.map(c => 
      c.id === controller.id ? { ...c, value } : c
    )
    const updatedTool = { ...tool, controllers: updatedControllers, updatedAt: Date.now() }
    await storage.saveTool(updatedTool)
    onControllerChange(controller.id, value)
  }

  const handleAddController = async () => {
    const newController = createDefaultController('slider', `Control ${tool.controllers.length + 1}`, tool.controllers.length)
    newController.section = sections[0] || 'Main'
    const updatedControllers = [...tool.controllers, newController]
    const updatedTool = { ...tool, controllers: updatedControllers, updatedAt: Date.now() }
    await storage.saveTool(updatedTool)
  }

  const handleDeleteController = async (controllerId: string) => {
    const updatedControllers = tool.controllers.filter(c => c.id !== controllerId)
    const updatedTool = { ...tool, controllers: updatedControllers, updatedAt: Date.now() }
    await storage.saveTool(updatedTool)
  }

  const handleUpdateController = async (updatedController: Controller) => {
    const updatedControllers = tool.controllers.map(c => 
      c.id === updatedController.id ? updatedController : c
    )
    const updatedTool = { ...tool, controllers: updatedControllers, updatedAt: Date.now() }
    await storage.saveTool(updatedTool)
    setEditingController(null)
  }

  const handleSectionToggle = (section: string) => {
    setExpandedSections(prev => ({ ...prev, [section]: !prev[section] }))
  }

  const getSectionControllers = (section: string) => 
    tool.controllers
      .filter(c => (c.section || 'Main') === section)
      .sort((a, b) => a.order - b.order)

  const renderController = (controller: Controller) => {
    if (editMode) {
      return (
        <ControllerEditor
          key={controller.id}
          controller={controller}
          onSave={handleUpdateController}
          onCancel={() => setEditingController(null)}
          onDelete={() => handleDeleteController(controller.id)}
          sections={sections}
        />
      )
    }

    const isEditing = editingController?.id === controller.id
    if (isEditing) {
      return (
        <ControllerEditor
          key={controller.id}
          controller={controller}
          onSave={handleUpdateController}
          onCancel={() => setEditingController(null)}
          onDelete={() => handleDeleteController(controller.id)}
          sections={sections}
        />
      )
    }

    return (
      <ControllerInput
        key={controller.id}
        controller={controller}
        value={controller.value}
        onChange={(v) => handleValueChange(controller, v)}
      />
    )
  }

  return (
    <div className="bg-[#111] border-l border-[#222] flex flex-col" style={{ width, minWidth: 280, maxWidth: 500 }}>
      <div className="flex items-center justify-between p-3 border-b border-[#222]">
        <h3 className="text-white font-medium">Controllers</h3>
        <div className="flex items-center gap-1">
          <button
            onClick={() => setEditMode(!editMode)}
            className={`p-1.5 rounded transition-colors ${editMode ? 'bg-[#8b5cf6]/20 text-[#8b5cf6]' : 'text-[#666] hover:text-white hover:bg-[#222]'}`}
            title={editMode ? 'Exit edit mode' : 'Edit controllers'}
          >
            <Edit2 className="w-4 h-4" />
          </button>
          <button
            onClick={handleAddController}
            className="p-1.5 rounded transition-colors text-[#666] hover:text-white hover:bg-[#222]"
            title="Add controller"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-3 space-y-3" style={{ minHeight: 0 }}>
        {sections.length === 0 ? (
          <div className="text-center text-[#666] text-sm py-8">
            No controllers yet. Click + to add one.
          </div>
        ) : (
          sections.map(section => {
            const controllers = getSectionControllers(section)
            const isExpanded = expandedSections[section] !== false
            return (
              <div key={section} className="space-y-2">
                <button
                  onClick={() => handleSectionToggle(section)}
                  className="flex items-center justify-between w-full px-2 py-1.5 text-xs font-medium text-[#888] hover:text-white rounded"
                >
                  <span className="flex items-center gap-1.5">
                    {isExpanded ? (
                      <ChevronDown className="w-3 h-3" />
                    ) : (
                      <ChevronRight className="w-3 h-3" />
                    )}
                    {section}
                    <span className="px-1.5 py-0.5 text-[10px] bg-[#222] rounded">{controllers.length}</span>
                  </span>
                </button>
                {isExpanded && (
                  <div className="space-y-2 ml-2 border-l border-[#222] pl-3">
                    {controllers.map(renderController)}
                  </div>
                )}
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}

function ControllerInput({ 
  controller, 
  value, 
  onChange 
}: { 
  controller: Controller
  value: unknown
  onChange: (v: unknown) => void
}) {
  const { type, label, min, max, step, options, placeholder, accept } = controller

  switch (type) {
    case 'slider':
    case 'number': {
      const numValue = typeof value === 'number' ? value : 0
      return (
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <label className="text-sm text-white">{label}</label>
            <span className="text-xs text-[#888] font-mono">
              {type === 'slider' ? (numValue * 100).toFixed(0) + '%' : numValue}
            </span>
          </div>
          <input
            type="range"
            min={min ?? 0}
            max={max ?? (type === 'slider' ? 1 : 100)}
            step={step ?? (type === 'slider' ? 0.01 : 1)}
            value={numValue}
            onChange={(e) => onChange(parseFloat(e.target.value))}
            className="w-full h-1.5 bg-[#222] rounded appearance-none accent-[#8b5cf6]"
          />
        </div>
      )
    }
    case 'toggle': {
      const boolValue = Boolean(value)
      return (
        <label className="flex items-center justify-between cursor-pointer">
          <span className="text-sm text-white">{label}</span>
          <button
            type="button"
            onClick={() => onChange(!boolValue)}
            className={`relative w-10 h-6 rounded-full transition-colors ${
              boolValue ? 'bg-[#8b5cf6]' : 'bg-[#333]'
            }`}
          >
            <span
              className={`absolute top-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform ${
                boolValue ? 'translate-x-5' : 'translate-x-0.5'
              }`}
            />
          </button>
        </label>
      )
    }
    case 'dropdown': {
      return (
        <div className="space-y-1.5">
          <label className="text-sm text-white">{label}</label>
          <select
            value={value as string}
            onChange={(e) => onChange(e.target.value)}
            className="w-full bg-[#1a1a1a] border border-[#333] rounded-lg px-3 py-2 text-white text-sm focus:outline-none focus:border-[#8b5cf6]"
          >
            {options?.map((opt, i) => (
              <option key={i} value={opt}>{opt}</option>
            ))}
          </select>
        </div>
      )
    }
    case 'color': {
      return (
        <div className="space-y-1.5">
          <label className="text-sm text-white">{label}</label>
          <input
            type="color"
            value={value as string}
            onChange={(e) => onChange(e.target.value)}
            className="w-full h-10 rounded-lg border border-[#333] cursor-pointer bg-transparent"
          />
        </div>
      )
    }
    case 'text': {
      return (
        <div className="space-y-1.5">
          <label className="text-sm text-white">{label}</label>
          <input
            type="text"
            value={value as string}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder}
            className="w-full bg-[#1a1a1a] border border-[#333] rounded-lg px-3 py-2 text-white text-sm placeholder-[#666] focus:outline-none focus:border-[#8b5cf6]"
          />
        </div>
      )
    }
    case 'file': {
      return (
        <div className="space-y-1.5">
          <label className="text-sm text-white">{label}</label>
          <input
            type="file"
            accept={accept}
            onChange={(e) => onChange(e.target.files)}
            className="w-full text-sm text-[#888] file:mr-4 file:py-1 file:px-3 file:rounded file:border-0 file:text-sm file:font-medium file:bg-[#8b5cf6]/20 file:text-[#8b5cf6] hover:file:bg-[#8b5cf6]/30"
          />
        </div>
      )
    }
    case 'button': {
      return (
        <button
          type="button"
          onClick={() => onChange(true)}
          className="w-full bg-[#222] hover:bg-[#333] border border-[#333] text-white px-4 py-2 rounded-lg text-sm transition-colors"
        >
          {label}
        </button>
      )
    }
    default:
      return null
  }
}

function ControllerEditor({ 
  controller, 
  onSave, 
  onCancel, 
  onDelete, 
  sections 
}: { 
  controller: Controller
  onSave: (c: Controller) => void
  onCancel: () => void
  onDelete: () => void
  sections: string[]
}) {
  const [form, setForm] = useState({
    id: controller.id,
    label: controller.label,
    type: controller.type,
    section: controller.section || 'Main',
    min: controller.min ?? 0,
    max: controller.max ?? 1,
    step: controller.step ?? 0.01,
    options: controller.options?.join('\n') || '',
    placeholder: controller.placeholder || '',
    accept: controller.accept || 'image/*,video/*',
  })

  const handleChange = (field: string, value: any) => {
    setForm(prev => ({ ...prev, [field]: value }))
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const updatedController: Controller = {
      ...controller,
      label: form.label,
      type: form.type,
      section: form.section,
      min: form.min,
      max: form.max,
      step: form.step,
      options: form.type === 'dropdown' ? form.options.split('\n').filter(Boolean) : controller.options,
      placeholder: form.placeholder,
      accept: form.accept,
    }
    onSave(updatedController)
  }

  return (
    <form onSubmit={handleSubmit} className="bg-[#1a1a1a] border border-[#333] rounded-lg p-3 space-y-3">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-medium text-white">Edit Controller</h4>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={onCancel}
            className="p-1 text-[#888] hover:text-white transition-colors"
            title="Cancel"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
          <button
            type="button"
            onClick={onDelete}
            className="p-1 text-red-500 hover:text-red-400 transition-colors"
            title="Delete"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      </div>

      <div className="space-y-3">
        <div>
          <label className="text-xs text-[#888] block mb-1">Label</label>
          <input
            type="text"
            value={form.label}
            onChange={(e) => handleChange('label', e.target.value)}
            className="w-full bg-[#111] border border-[#333] rounded px-2 py-1.5 text-white text-sm focus:outline-none focus:border-[#8b5cf6]"
          />
        </div>

        <div>
          <label className="text-xs text-[#888] block mb-1">Type</label>
          <select
            value={form.type}
            onChange={(e) => handleChange('type', e.target.value)}
            className="w-full bg-[#111] border border-[#333] rounded px-2 py-1.5 text-white text-sm focus:outline-none focus:border-[#8b5cf6]"
          >
            {['slider', 'toggle', 'dropdown', 'color', 'text', 'file', 'button', 'number'].map(t => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        </div>

        <div>
          <label className="text-xs text-[#888] block mb-1">Section</label>
          <select
            value={form.section}
            onChange={(e) => handleChange('section', e.target.value)}
            className="w-full bg-[#111] border border-[#333] rounded px-2 py-1.5 text-white text-sm focus:outline-none focus:border-[#8b5cf6]"
          >
            {sections.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>

        {['slider', 'number'].includes(form.type) && (
          <div className="grid grid-cols-3 gap-2">
            <div>
              <label className="text-xs text-[#888] block mb-1">Min</label>
              <input
                type="number"
                value={form.min}
                onChange={(e) => handleChange('min', parseFloat(e.target.value))}
                className="w-full bg-[#111] border border-[#333] rounded px-2 py-1.5 text-white text-sm focus:outline-none focus:border-[#8b5cf6]"
              />
            </div>
            <div>
              <label className="text-xs text-[#888] block mb-1">Max</label>
              <input
                type="number"
                value={form.max}
                onChange={(e) => handleChange('max', parseFloat(e.target.value))}
                className="w-full bg-[#111] border border-[#333] rounded px-2 py-1.5 text-white text-sm focus:outline-none focus:border-[#8b5cf6]"
              />
            </div>
            <div>
              <label className="text-xs text-[#888] block mb-1">Step</label>
              <input
                type="number"
                value={form.step}
                onChange={(e) => handleChange('step', parseFloat(e.target.value))}
                className="w-full bg-[#111] border border-[#333] rounded px-2 py-1.5 text-white text-sm focus:outline-none focus:border-[#8b5cf6]"
              />
            </div>
          </div>
        )}

        {form.type === 'dropdown' && (
          <div>
            <label className="text-xs text-[#888] block mb-1">Options (one per line)</label>
            <textarea
              value={form.options}
              onChange={(e) => handleChange('options', e.target.value)}
              rows={3}
              className="w-full bg-[#111] border border-[#333] rounded px-2 py-1.5 text-white text-sm focus:outline-none focus:border-[#8b5cf6] resize-none"
            />
          </div>
        )}

        {form.type === 'text' && (
          <div>
            <label className="text-xs text-[#888] block mb-1">Placeholder</label>
            <input
              type="text"
              value={form.placeholder}
              onChange={(e) => handleChange('placeholder', e.target.value)}
              className="w-full bg-[#111] border border-[#333] rounded px-2 py-1.5 text-white text-sm focus:outline-none focus:border-[#8b5cf6]"
            />
          </div>
        )}

        {form.type === 'file' && (
          <div>
            <label className="text-xs text-[#888] block mb-1">Accept</label>
            <input
              type="text"
              value={form.accept}
              onChange={(e) => handleChange('accept', e.target.value)}
              className="w-full bg-[#111] border border-[#333] rounded px-2 py-1.5 text-white text-sm focus:outline-none focus:border-[#8b5cf6]"
            />
          </div>
        )}
      </div>

      <div className="flex justify-end gap-2 pt-2 border-t border-[#222]">
        <button
          type="button"
          onClick={onCancel}
          className="px-3 py-1.5 text-sm text-[#888] hover:text-white transition-colors"
        >
          Cancel
        </button>
        <button
          type="submit"
          className="px-3 py-1.5 text-sm bg-[#8b5cf6] text-black font-medium rounded hover:bg-[#7c3aed] transition-colors"
        >
          Save
        </button>
      </div>
    </form>
  )
}