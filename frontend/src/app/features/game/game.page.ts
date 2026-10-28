import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AdventureApi } from '../../core/api/adventure.api';
import { GameState } from '../../core/api/models';
import { SessionStore } from '../../core/session.store';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-game-page',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './game.page.html',
  styleUrl: './game.page.scss',
})
export class GamePage {
  private readonly api = inject(AdventureApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sessionStore = inject(SessionStore);

  readonly game = signal<GameState | null>(null);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly saving = signal(false);
  readonly showSaveInput = signal(false);
  readonly error = signal('');
  readonly saved = signal('');
  readonly saveName = signal('My adventure');
  readonly sessionId = this.route.snapshot.paramMap.get('sessionId') ?? '';

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');

    this.api
      .game(this.sessionId)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (game) => this.accept(game),
        error: (err) => this.error.set(this.extractMessage(err)),
      });
  }

  choose(optionId: string): void {
    const current = this.game();
    if (!current || this.submitting() || current.status !== 'ACTIVE') {
      return;
    }

    this.submitting.set(true);
    this.error.set('');
    this.saved.set('');

    this.api
      .choose(this.sessionId, optionId, current.revision, crypto.randomUUID())
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (game) => this.accept(game),
        error: (err) => this.failMove(err),
      });
  }

  openSaveDialog(): void { this.showSaveInput.set(true); }

  cancelSave(): void { this.showSaveInput.set(false); }

  confirmSave(): void {
    const trimmed = this.saveName().trim();
    if (!trimmed || this.saving()) { return; }

    this.saving.set(true);
    this.error.set('');

    this.api
      .save(this.sessionId, trimmed)
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.saved.set(`Progress saved as "${trimmed}". You can restore it from the library.`);
          this.showSaveInput.set(false);
        },
        error: (err) => this.error.set(this.extractMessage(err)),
      });
  }

  choiceHint(index: number): string {
    return index === 0
      ? 'Trust your instincts and see where the path leads.'
      : 'This choice may change what comes next.';
  }

  library(): void { void this.router.navigateByUrl('/'); }

  private accept(game: GameState): void {
    this.game.set(game);
    if (game.status === 'ACTIVE') {
      this.sessionStore.setActive(game.sessionId);
    } else {
      this.sessionStore.clearActive();
    }
    queueMicrotask(() => {
      document.querySelector<HTMLElement>('#episode')?.focus();
    });
  }

  private failMove(err: unknown): void {
    const response = err as HttpErrorResponse;
    if (response.status === 409 && response.error?.sessionId) {
      this.accept(response.error as GameState);
      this.error.set('The game changed in another request. The latest state is shown.');
      return;
    }
    this.error.set(this.extractMessage(err));
  }

  private extractMessage(err: unknown): string {
    const response = err as (HttpErrorResponse & { userMessage?: string }) | undefined;
    return (
      response?.userMessage ??
      (response?.error as { message?: string } | undefined)?.message ??
      'The request could not be completed. Your last confirmed state is still shown.'
    );
  }
}
