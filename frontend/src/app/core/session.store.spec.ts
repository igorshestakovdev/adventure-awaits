import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { SessionStore } from './session.store';

describe('SessionStore', () => {
  let store: SessionStore;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    store = TestBed.inject(SessionStore);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('initialises with null when localStorage is empty', () => {
    expect(store.activeSessionId()).toBeNull();
  });

  it('reads an existing session id from localStorage on construction', () => {
    localStorage.setItem('adventure.activeSession', 'existing-session');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    const freshStore = TestBed.inject(SessionStore);
    expect(freshStore.activeSessionId()).toBe('existing-session');
  });

  it('sets the active session id in signal and localStorage', () => {
    store.setActive('session-abc');
    expect(store.activeSessionId()).toBe('session-abc');
    expect(localStorage.getItem('adventure.activeSession')).toBe('session-abc');
  });

  it('clears the active session id from signal and localStorage', () => {
    store.setActive('session-abc');
    store.clearActive();
    expect(store.activeSessionId()).toBeNull();
    expect(localStorage.getItem('adventure.activeSession')).toBeNull();
  });
});
