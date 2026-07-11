import './globals.css'
import type { Metadata } from 'next'

export const metadata: Metadata = {
  title: 'Insidher — Private Receptionist & Personal Assistant',
  description: 'Discreet, personalized front desk for independent professionals. Clients meet your custom receptionist. You work with Anita, your sovereign PA.',
  openGraph: {
    title: 'Insidher — Private Receptionist & Personal Assistant',
    description: 'Discreet, personalized front desk for independent professionals.',
    type: 'website',
  },
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
      </head>
      <body>{children}</body>
    </html>
  )
}