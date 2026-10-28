import { Injectable, signal } from '@angular/core';

const SESSION_KEY = 'adventure.activeSession';

@Injectable({ providedIn: 'root' })
export class SessionStore {
  readonly activeSessionId = signal<string | null>(localStorage.getItem(SESSION_KEY));

  setActive(id: string): void {
    localStorage.setItem(SESSION_KEY, id);
    this.activeSessionId.set(id);
  }

  clearActive(): void {
    localStorage.removeItem(SESSION_KEY);
    this.activeSessionId.set(null);
  }
}
