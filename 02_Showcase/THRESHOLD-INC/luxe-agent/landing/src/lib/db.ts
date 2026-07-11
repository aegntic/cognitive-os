import fs from 'fs'
import path from 'path'

const dataDir = path.join(process.cwd(), 'data')
const waitlistPath = path.join(dataDir, 'waitlist.json')

if (!fs.existsSync(dataDir)) {
  fs.mkdirSync(dataDir, { recursive: true })
}

if (!fs.existsSync(waitlistPath)) {
  fs.writeFileSync(waitlistPath, JSON.stringify([]))
}

export interface WaitlistEntry {
  id: number
  email: string
  name?: string
  phone?: string
  source: string
  referrer?: string
  utm_source?: string
  utm_medium?: string
  utm_campaign?: string
  created_at: string
}

function readWaitlist(): WaitlistEntry[] {
  try {
    const data = fs.readFileSync(waitlistPath, 'utf-8')
    return JSON.parse(data)
  } catch {
    return []
  }
}

function writeWaitlist(list: WaitlistEntry[]) {
  fs.writeFileSync(waitlistPath, JSON.stringify(list, null, 2))
}

export function addToWaitlist(data: {
  email: string
  name?: string
  phone?: string
  source?: string
  referrer?: string
  utm_source?: string
  utm_medium?: string
  utm_campaign?: string
}): { success: boolean; error?: string } {
  const list = readWaitlist()
  const normalizedEmail = data.email.toLowerCase().trim()

  if (list.find(e => e.email === normalizedEmail)) {
    return { success: false, error: 'Email already registered' }
  }

  const newEntry: WaitlistEntry = {
    id: Date.now(),
    email: normalizedEmail,
    name: data.name?.trim() || undefined,
    phone: data.phone?.trim() || undefined,
    source: data.source || 'landing',
    referrer: data.referrer || undefined,
    utm_source: data.utm_source || undefined,
    utm_medium: data.utm_medium || undefined,
    utm_campaign: data.utm_campaign || undefined,
    created_at: new Date().toISOString()
  }

  list.unshift(newEntry)
  writeWaitlist(list)
  return { success: true }
}

export function getWaitlistCount(): number {
  return readWaitlist().length
}