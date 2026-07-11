import { NextRequest, NextResponse } from 'next/server'
import { addToWaitlist, getWaitlistCount } from '../../../lib/db'

export async function POST(request: NextRequest) {
  try {
    const body = await request.json()
    const { email, name, phone, source, referrer, utm_source, utm_medium, utm_campaign } = body

    if (!email || !email.includes('@')) {
      return NextResponse.json({ success: false, error: 'Valid email required' }, { status: 400 })
    }

    const result = addToWaitlist({
      email,
      name,
      phone,
      source: source || 'landing',
      referrer: referrer || request.headers.get('referer') || undefined,
      utm_source,
      utm_medium,
      utm_campaign
    })

    if (!result.success) {
      return NextResponse.json({ success: false, error: result.error }, { status: 409 })
    }

    const count = getWaitlistCount()
    return NextResponse.json({ success: true, count, message: 'Welcome to the waitlist' })
  } catch {
    return NextResponse.json({ success: false, error: 'Server error' }, { status: 500 })
  }
}

export async function GET() {
  const count = getWaitlistCount()
  return NextResponse.json({ count })
}