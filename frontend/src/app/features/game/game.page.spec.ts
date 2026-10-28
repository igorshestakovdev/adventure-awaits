import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AdventureApi } from '../../core/api/adventure.api';
import { GameState } from '../../core/api/models';
import { GamePage } from './game.page';

const mockGame: GameState = {
  sessionId: 'session-123',
  bookId: 'clockwork-lighthouse',
  bookTitle: 'The Clockwork Lighthouse',
  sectionId: 1,
  text: 'You stand before the lighthouse.',
  choices: [
    { id: 'opt-1', description: 'Enter the front door' },
    { id: 'opt-2', description: 'Search the perimeter' },
  ],
  health: 10,
  status: 'ACTIVE',
  revision: 1,
  consequence: null,
};

describe('GamePage', () => {
  const createComponent = async (apiOverrides: Partial<AdventureApi> = {}) => {
    const api = {
      game: vi.fn(() => of(mockGame)),
      choose: vi.fn(() => of({ ...mockGame, revision: 2, sectionId: 2 })),
      save: vi.fn(() =>
        of({ id: 'save-1', name: 'My adventure', bookId: 'clockwork-lighthouse', savedAt: 'now' })
      ),
      ...apiOverrides,
    };

    await TestBed.configureTestingModule({
      imports: [GamePage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'session-123' } } },
        },
        { provide: AdventureApi, useValue: api },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(GamePage);
    fixture.detectChanges();

    return { fixture, component: fixture.componentInstance, api };
  };

  it('loads game on initialization', async () => {
    const { component, fixture, api } = await createComponent();

    expect(api.game).toHaveBeenCalledWith('session-123');
    expect(component.game()?.bookTitle).toBe('The Clockwork Lighthouse');
    expect(fixture.nativeElement.textContent).toContain('You stand before the lighthouse.');
  });

  it('submits a choice with expectedRevision and uuid', async () => {
    const { component, api } = await createComponent();

    component.choose('opt-1');

    expect(api.choose).toHaveBeenCalledWith(
      'session-123',
      'opt-1',
      1,
      expect.any(String)
    );
    expect(component.game()?.revision).toBe(2);
  });

  it('prevents making choice when game status is not ACTIVE', async () => {
    const { component, api } = await createComponent({
      game: vi.fn(() => of({ ...mockGame, status: 'DEAD' as const })),
    });

    component.choose('opt-1');

    expect(api.choose).not.toHaveBeenCalled();
  });

  it('recovers gracefully when server returns 409 conflict', async () => {
    const conflictState: GameState = {
      ...mockGame,
      revision: 3,
      sectionId: 3,
      text: 'Concurrent move was accepted.',
    };
    const errorResponse = new HttpErrorResponse({
      status: 409,
      error: conflictState,
    });

    const { component } = await createComponent({
      choose: vi.fn(() => throwError(() => errorResponse)),
    });

    component.choose('opt-1');

    expect(component.game()?.revision).toBe(3);
    expect(component.error()).toContain('The game changed in another request');
  });

  it('allows saving progress with a custom name', async () => {
    const { component, api } = await createComponent();

    component.openSaveDialog();
    component.saveName.set('Chapter 2 Checkpoint');
    component.confirmSave();

    expect(api.save).toHaveBeenCalledWith('session-123', 'Chapter 2 Checkpoint');
    expect(component.saved()).toContain('Chapter 2 Checkpoint');
    expect(component.showSaveInput()).toBe(false);
  });

  it('navigates back to library', async () => {
    const { component } = await createComponent();
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    component.library();

    expect(navigateSpy).toHaveBeenCalledWith('/');
  });

  it('retains confirmed state after a rejected move', async () => {
    const { component } = await createComponent({
      choose: vi.fn(() => throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'Invalid choice' } }))),
    });

    component.choose('missing');

    expect(component.game()).toEqual(mockGame);
    expect(component.error()).toBe('Invalid choice');
    expect(component.submitting()).toBe(false);
  });

  it('does not submit another choice while a move is pending', async () => {
    const pending = new Subject<GameState>();
    const { component, api } = await createComponent({ choose: vi.fn(() => pending) });

    component.choose('opt-1');
    component.choose('opt-2');

    expect(api.choose).toHaveBeenCalledTimes(1);
    expect(component.game()).toEqual(mockGame);
    pending.next({ ...mockGame, health: 4, revision: 2 });
    pending.complete();
    expect(component.game()?.health).toBe(4);
    expect(component.submitting()).toBe(false);
  });

  it('rejects blank saves, supports cancellation and reports save errors', async () => {
    const { component, api } = await createComponent({
      save: vi.fn(() => throwError(() => new HttpErrorResponse({ status: 500 }))),
    });
    component.openSaveDialog();
    component.cancelSave();
    expect(component.showSaveInput()).toBe(false);
    component.saveName.set('  ');
    component.confirmSave();
    expect(api.save).not.toHaveBeenCalled();

    component.openSaveDialog();
    component.saveName.set('Checkpoint');
    component.confirmSave();

    expect(component.error()).toContain('could not be completed');
    expect(component.saving()).toBe(false);
    expect(component.showSaveInput()).toBe(true);
    expect(component.saved()).toBe('');
  });

  it('renders consequence banner when move result has consequence', async () => {
    const { fixture } = await createComponent({
      game: vi.fn(() =>
        of({
          ...mockGame,
          consequence: { text: 'You strike the far ledge hard, but keep your grip.', healthDelta: -5 },
        })
      ),
    });

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.consequence')).toBeTruthy();
    expect(el.querySelector('.consequence-badge')?.textContent).toContain('-5 HP');
    expect(el.querySelector('.consequence-title')?.textContent).toContain('You lose 5 HP');
    expect(el.querySelector('.consequence-text')?.textContent).toContain(
      'You strike the far ledge hard, but keep your grip.'
    );
  });
});
