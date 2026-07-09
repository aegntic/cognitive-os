// Brik Pro - Panel Resize Handle
import { useRef, useEffect } from 'react'
import { GripVertical, GripHorizontal } from 'lucide-react'

interface PanelResizeHandleProps {
  position: number
  onDrag: (position: number) => void
  min: number
  max: number
  direction: 'horizontal' | 'vertical'
  reverse?: boolean
}

export function PanelResizeHandle({ position, onDrag, min, max, direction, reverse }: PanelResizeHandleProps) {
  const handleRef = useRef<HTMLDivElement>(null)
  const startX = useRef(0)
  const startY = useRef(0)
  const startPosition = useRef(0)

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      const delta = direction === 'horizontal' 
        ? (reverse ? startX.current - e.clientX : e.clientX - startX.current)
        : (reverse ? startY.current - e.clientY : e.clientY - startY.current)
      const newPosition = Math.max(min, Math.max(max, startPosition.current + delta))
      onDrag(newPosition)
    }

    const handleMouseUp = () => {
      document.removeEventListener('mousemove', handleMouseMove)
      document.removeEventListener('mouseup', handleMouseUp)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }

    const handleMouseDown = (e: MouseEvent) => {
      e.preventDefault()
      startX.current = e.clientX
      startY.current = e.clientY
      startPosition.current = position
      document.addEventListener('mousemove', handleMouseMove)
      document.addEventListener('mouseup', handleMouseUp)
      document.body.style.cursor = direction === 'horizontal' ? 'col-resize' : 'row-resize'
      document.body.style.userSelect = 'none'
    }

    const handle = handleRef.current
    if (handle) {
      handle.addEventListener('mousedown', handleMouseDown)
    }
    return () => {
      if (handle) {
        handle.removeEventListener('mousedown', handleMouseDown)
      }
    }
  }, [position, onDrag, min, max, direction, reverse])

  return (
    <div
      ref={handleRef}
      className={`
        flex items-center justify-center select-none transition-colors
        ${direction === 'horizontal' 
          ? 'w-1 h-full cursor-col-resize hover:bg-[#8b5cf6]/30 bg-transparent' 
          : 'h-1 w-full cursor-row-resize hover:bg-[#8b5cf6]/30 bg-transparent'}
      `}
      style={{
        [direction === 'horizontal' ? (reverse ? 'right' : 'left') : (reverse ? 'bottom' : 'top')]: 0,
      }}
    >
      {direction === 'horizontal' ? (
        <GripVertical className="w-4 h-4 text-[#333] hover:text-[#8b5cf6] transition-colors" />
      ) : (
        <GripHorizontal className="w-4 h-4 text-[#333] hover:text-[#8b5cf6] transition-colors" />
      )}
    </div>
  )
}