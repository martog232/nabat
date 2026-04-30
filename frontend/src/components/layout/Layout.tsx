import type { ReactNode } from 'react'
import { Navbar } from './Navbar'

export function Layout({ children }: { children: ReactNode }) {
  return (
    <div className="relative w-full h-full">
      <Navbar />
      {children}
    </div>
  )
}
