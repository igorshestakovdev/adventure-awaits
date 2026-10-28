import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, HostListener, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, finalize, forkJoin, of } from 'rxjs';
import { AdventureApi } from '../../core/api/adventure.api';
import { ApiError, BookSummary, SaveSummary } from '../../core/api/models';
import { SessionStore } from '../../core/session.store';

export interface BookPresentation {
  displayTitle: string;
  displayAuthor: string;
  summary: string;
  difficulty: 'Easy' | 'Medium' | 'Hard';
  genre: string;
  readingTime: string;
  chapters: string;
  tags: string[];
}

const BOOK_PRESENTATION: Record<string, Partial<BookPresentation>> = {
  'The Crystal Caverns': {
    displayTitle: 'The Crystal Caverns',
    displayAuthor: 'Elena Brightwater',
    summary:
      'Deep beneath the mountain lies a network of crystal caves filled with ancient magic and dangerous creatures. Your choices will determine whether you emerge as a hero or become another lost soul in the depths.',
    difficulty: 'Medium',
    genre: 'Fantasy',
    readingTime: '45-60 min',
    chapters: '12 chapters',
    tags: ['Magic', 'Underground', 'Crystals'],
  },
  'Pirates of the Jade Sea': {
    displayTitle: 'Pirates of the Jade Sea',
    displayAuthor: 'Captain Blackwater',
    summary:
      'Set sail on the treacherous Jade Sea where pirates rule and treasure awaits the bold. Navigate through storms, rival crews, and ancient curses in this swashbuckling adventure.',
    difficulty: 'Easy',
    genre: 'Adventure',
    readingTime: '30-45 min',
    chapters: '8 chapters',
    tags: ['Pirates', 'Ocean', 'Treasure'],
  },
  "The Last Wizard's Tower": {
    displayTitle: "The Last Wizard's Tower",
    displayAuthor: 'Sage Moonshadow',
    summary:
      "The realm's last wizard has vanished, leaving behind only his tower and a terrible curse spreading across the land. Uncover the secrets within and decide the fate of magic itself.",
    difficulty: 'Hard',
    genre: 'High Fantasy',
    readingTime: '60-90 min',
    chapters: '18 chapters',
    tags: ['Wizards', 'Magic', 'Tower'],
  },
  'The Prisoner': {
    displayTitle: 'The Prisoner',
    displayAuthor: 'Warden Grey',
    summary:
      'Trapped in a labyrinthine fortress with no memory of your crime, you must uncover the truth and find a way to escape before the sentence is carried out.',
    difficulty: 'Hard',
    genre: 'Mystery',
    readingTime: '50-70 min',
    chapters: '15 chapters',
    tags: ['Mystery', 'Escape', 'Fantasy'],
  },
  'The Clockwork Lighthouse': {
    displayTitle: 'The Clockwork Lighthouse',
    displayAuthor: 'Barnaby Quill',
    summary:
      'A storm has silenced the clockwork lighthouse. Brave treacherous cliffs and forgotten mechanisms to restore the beacon before dawn.',
    difficulty: 'Easy',
    genre: 'Steampunk Mystery',
    readingTime: '20-30 min',
    chapters: '6 chapters',
    tags: ['Steampunk', 'Lighthouse', 'Adventure'],
  },
};

export const EXAMPLE_QUEST_JSON = JSON.stringify(
  {
    title: 'The Whispering Tombs',
    author: 'Unknown',
    difficulty: 'EASY',
    sections: [
      {
        id: 1,
        text: 'You find yourself standing at the arched entrance of the ancient Whispering Tombs. Two stone corridors stretch before you into the darkness.',
        type: 'BEGIN',
        options: [
          {
            description: 'Take the moss-covered hallway to the left',
            gotoId: 2,
          },
          {
            description: 'Follow the torch-lit passage to the right',
            gotoId: 3,
          },
        ],
      },
      {
        id: 2,
        text: 'A narrow passage leads to a submerged chamber. Stepping stones seem to lead to a glowing pedestal.',
        type: 'NODE',
        options: [
          {
            description: 'Carefully leap across the slippery stones',
            gotoId: 4,
            consequence: {
              type: 'LOSE_HEALTH',
              value: 2,
              text: 'You slip on slick algae and scrape your knee against the jagged rock.',
            },
          },
          {
            description: 'Search the dry crevices for another route',
            gotoId: 3,
          },
        ],
      },
      {
        id: 3,
        text: 'You discover a resting alcove with an ancient fountain bubbling with blessed starlight water.',
        type: 'NODE',
        options: [
          {
            description: 'Drink from the revitalizing fountain',
            gotoId: 4,
            consequence: {
              type: 'GAIN_HEALTH',
              value: 3,
              text: 'The celestial water restores your strength and clears your mind.',
            },
          },
          {
            description: 'Press onward without drinking',
            gotoId: 4,
          },
        ],
      },
      {
        id: 4,
        text: 'You reach the heart of the tomb where the Golden Scepter of Antiquity rests upon a marble altar. Sunlight pours through the ceiling crevice as you claim the relic. You emerge victorious into the sunlit valley!',
        type: 'END',
        options: [],
      },
    ],
  },
  null,
  2,
);

@Component({
  selector: 'app-library-page',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './library.page.html',
  styleUrl: './library.page.scss',
})
export class LibraryPage {
  private readonly api = inject(AdventureApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sessionStore = inject(SessionStore);
  private readonly catalog = signal<BookSummary[]>([]);

  readonly query = signal('');
  readonly difficulty = signal('');
  readonly genre = signal('');
  readonly books = computed(() => {
    const term = this.query().trim().toLowerCase();
    const difficulty = this.difficulty().toUpperCase();
    const genre = this.genre();

    return this.catalog().filter((book) => {
      const meta = this.bookMeta(book);
      const bookDiff = (meta.difficulty || book.difficulty).toUpperCase();
      const matchesDifficulty =
        !difficulty ||
        bookDiff === difficulty ||
        book.difficulty.toUpperCase() === difficulty;

      const keywords = this.allBookKeywords(book);
      const matchesGenre = !genre || keywords.includes(genre) || this.bookTags(book).includes(genre);

      const matchesTerm =
        !term ||
        book.title.toLowerCase().includes(term) ||
        meta.displayTitle.toLowerCase().includes(term) ||
        book.author.toLowerCase().includes(term) ||
        meta.displayAuthor.toLowerCase().includes(term);

      return matchesDifficulty && matchesGenre && matchesTerm;
    });
  });
  readonly saves = signal<SaveSummary[]>([]);
  readonly loading = signal(true);
  readonly busyBook = signal<string | null>(null);
  readonly busyDelete = signal<string | null>(null);
  readonly confirmingDeleteId = signal<string | null>(null);
  readonly error = signal('');

  readonly isImportModalOpen = signal(false);
  readonly selectedFile = signal<File | null>(null);
  readonly validating = signal(false);
  readonly validationErrors = signal<string[]>([]);
  readonly validationSuccess = signal<string | null>(null);
  readonly isValidFile = signal(false);
  readonly importing = signal(false);
  readonly importError = signal<string | null>(null);
  readonly exampleQuestJson = EXAMPLE_QUEST_JSON;
  readonly showExample = signal(false);
  readonly copiedExample = signal(false);

  readonly activeSession = computed(() => this.sessionStore.activeSessionId());
  readonly hasActiveAdventure = computed(
    () => Boolean(this.activeSession() && this.saves().length > 0),
  );

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.error.set('');

    forkJoin({
      books: this.api.books(),
      saves: this.api.saves().pipe(catchError(() => of([]))),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ books, saves }) => {
          this.catalog.set(books);
          this.saves.set(saves);
          if (saves.length === 0) {
            this.sessionStore.clearActive();
          }
        },
        error: (err) => this.fail(err),
      });
  }

  start(book: BookSummary): void {
    if (!book.playable || this.busyBook()) {
      return;
    }

    this.busyBook.set(book.id);
    this.error.set('');

    this.api
      .start(book.id)
      .pipe(
        finalize(() => this.busyBook.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (game) => this.openGame(game.sessionId),
        error: (err) => this.fail(err),
      });
  }

  continue(): void {
    const id = this.sessionStore.activeSessionId();
    if (id) {
      void this.router.navigate(['/game', id]);
    }
  }

  dismissActiveSession(): void {
    this.sessionStore.clearActive();
  }

  restore(save: SaveSummary): void {
    this.api
      .restore(save.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (game) => this.openGame(game.sessionId),
        error: (err) => this.fail(err),
      });
  }

  promptDelete(save: SaveSummary, event: Event): void {
    event.stopPropagation();
    this.confirmingDeleteId.set(save.id);
  }

  cancelDelete(event: Event): void {
    event.stopPropagation();
    this.confirmingDeleteId.set(null);
  }

  confirmDelete(save: SaveSummary, event: Event): void {
    event.stopPropagation();
    if (this.busyDelete()) {
      return;
    }

    this.busyDelete.set(save.id);
    this.error.set('');

    this.api
      .deleteSave(save.id)
      .pipe(
        finalize(() => this.busyDelete.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.saves.update((list) => list.filter((s) => s.id !== save.id));
          this.confirmingDeleteId.set(null);
          this.sessionStore.clearActive();
        },
        error: (err) => this.fail(err),
      });
  }

  toggleGenre(genre: string): void {
    this.genre.set(this.genre() === genre ? '' : genre);
  }

  toggleDifficulty(difficulty: string): void {
    this.difficulty.set(this.difficulty() === difficulty ? '' : difficulty);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.isImportModalOpen()) {
      this.closeImportModal();
    }
  }

  openImportModal(): void {
    this.isImportModalOpen.set(true);
    this.resetImportState();
  }

  closeImportModal(): void {
    this.isImportModalOpen.set(false);
    this.resetImportState();
  }

  toggleExample(): void {
    this.showExample.set(!this.showExample());
  }

  useExampleQuest(): void {
    const file = new File([this.exampleQuestJson], 'sample-quest.json', {
      type: 'application/json',
    });
    this.selectedFile.set(file);
    this.validating.set(false);
    this.importError.set(null);

    const result = this.validateQuestJson(file, this.exampleQuestJson);
    if (result.valid) {
      this.isValidFile.set(true);
      this.validationSuccess.set(result.summary ?? 'Valid sample quest');
      this.validationErrors.set([]);
    } else {
      this.isValidFile.set(false);
      this.validationSuccess.set(null);
      this.validationErrors.set(result.errors);
    }
  }

  copyExampleQuest(): void {
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      void navigator.clipboard.writeText(this.exampleQuestJson);
    }
    this.copiedExample.set(true);
    setTimeout(() => this.copiedExample.set(false), 2000);
  }

  resetImportState(): void {
    this.selectedFile.set(null);
    this.validating.set(false);
    this.validationErrors.set([]);
    this.validationSuccess.set(null);
    this.isValidFile.set(false);
    this.importing.set(false);
    this.importError.set(null);
    this.showExample.set(false);
  }

  async onFileSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      this.resetImportState();
      return;
    }

    this.selectedFile.set(file);
    this.validating.set(true);
    this.validationErrors.set([]);
    this.validationSuccess.set(null);
    this.isValidFile.set(false);
    this.importError.set(null);

    try {
      const text = await this.readFileText(file);
      const result = this.validateQuestJson(file, text);
      if (result.valid) {
        this.isValidFile.set(true);
        this.validationSuccess.set(result.summary ?? 'Valid quest file');
        this.validationErrors.set([]);
      } else {
        this.isValidFile.set(false);
        this.validationSuccess.set(null);
        this.validationErrors.set(result.errors);
      }
    } catch (err: unknown) {
      this.isValidFile.set(false);
      this.validationSuccess.set(null);
      this.validationErrors.set([
        'Could not read file: ' + (err instanceof Error ? err.message : String(err)),
      ]);
    } finally {
      this.validating.set(false);
    }
  }

  readFileText(file: File): Promise<string> {
    if (typeof file.text === 'function') {
      return file.text();
    }
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result ?? ''));
      reader.onerror = () => reject(reader.error ?? new Error('File reading failed'));
      reader.readAsText(file);
    });
  }

  validateQuestJson(
    file: File,
    text: string,
  ): { valid: boolean; errors: string[]; summary?: string } {
    const errors: string[] = [];

    if (file.size > 1_000_000) {
      errors.push('File size exceeds 1 MB limit');
    }

    if (file.size === 0 || !text.trim()) {
      errors.push('File is empty');
      return { valid: false, errors };
    }

    let parsed: any;
    try {
      parsed = JSON.parse(text);
    } catch {
      errors.push('Invalid JSON syntax');
      return { valid: false, errors };
    }

    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      errors.push('Quest file must be a JSON object');
      return { valid: false, errors };
    }

    if (typeof parsed.title !== 'string' || !parsed.title.trim()) {
      errors.push('Book title is required');
    }

    if (typeof parsed.author !== 'string' || !parsed.author.trim()) {
      errors.push('Author is required');
    }

    const diff =
      typeof parsed.difficulty === 'string' ? parsed.difficulty.trim().toUpperCase() : '';
    if (!['EASY', 'MEDIUM', 'HARD'].includes(diff)) {
      errors.push('Difficulty must be EASY, MEDIUM, or HARD');
    }

    if (!Array.isArray(parsed.sections) || parsed.sections.length === 0) {
      errors.push('Book must contain sections');
      return { valid: false, errors };
    }

    if (parsed.sections.length > 2000) {
      errors.push('Book contains more than 2000 sections');
    }

    const sectionIds = new Set<string>();
    let beginCount = 0;
    let endCount = 0;

    parsed.sections.forEach((section: any, index: number) => {
      if (typeof section !== 'object' || section === null) {
        errors.push(`Section at index ${index} is not an object`);
        return;
      }

      const idVal = section.id;
      if (idVal === undefined || idVal === null || !/^-?\d+$/.test(String(idVal).trim())) {
        errors.push(`Section at index ${index} has an invalid or missing numeric ID`);
      } else {
        const idStr = String(idVal).trim();
        if (sectionIds.has(idStr)) {
          errors.push(`Duplicate section id: ${idStr}`);
        } else {
          sectionIds.add(idStr);
        }
      }

      if (typeof section.text !== 'string' || !section.text.trim()) {
        errors.push(`Section ${section.id ?? index} is missing text`);
      }

      const type = typeof section.type === 'string' ? section.type.trim().toUpperCase() : '';
      if (!['BEGIN', 'NODE', 'END'].includes(type)) {
        errors.push(
          `Section ${section.id ?? index} has invalid type "${section.type}" (must be BEGIN, NODE, or END)`,
        );
      } else {
        if (type === 'BEGIN') {
          beginCount++;
        }
        if (type === 'END') {
          endCount++;
        }
      }
    });

    if (beginCount !== 1) {
      errors.push(`Book must contain exactly one BEGIN section; found ${beginCount}`);
    }

    if (endCount === 0) {
      errors.push('Book must contain at least one END section');
    }

    parsed.sections.forEach((section: any) => {
      if (typeof section !== 'object' || section === null) {
        return;
      }
      const type = typeof section.type === 'string' ? section.type.trim().toUpperCase() : '';
      if (type !== 'END') {
        if (!Array.isArray(section.options) || section.options.length === 0) {
          errors.push(`Non-ending section ${section.id} has no options`);
        } else {
          if (section.options.length > 50) {
            errors.push(`Section ${section.id} contains more than 50 options`);
          }
          section.options.forEach((opt: any, optIdx: number) => {
            if (typeof opt !== 'object' || opt === null) {
              errors.push(`Option ${optIdx + 1} in section ${section.id} is invalid`);
              return;
            }
            if (typeof opt.description !== 'string' || !opt.description.trim()) {
              errors.push(`Option ${optIdx + 1} in section ${section.id} is missing a description`);
            }
            if (
              opt.gotoId === undefined ||
              opt.gotoId === null ||
              !sectionIds.has(String(opt.gotoId).trim())
            ) {
              errors.push(`Section ${section.id} points to missing section ${opt.gotoId}`);
            }
          });
        }
      }
    });

    if (errors.length > 0) {
      return { valid: false, errors };
    }

    const summary = `«${parsed.title.trim()}» by ${parsed.author.trim()} (${parsed.sections.length} sections, Difficulty: ${diff})`;
    return { valid: true, errors: [], summary };
  }

  submitImport(): void {
    const file = this.selectedFile();
    if (!file || !this.isValidFile() || this.importing()) {
      return;
    }

    this.importing.set(true);
    this.importError.set(null);

    this.api
      .import(file)
      .pipe(
        finalize(() => this.importing.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.closeImportModal();
          this.refresh();
        },
        error: (err) => {
          const response = err as (HttpErrorResponse & { userMessage?: string }) | undefined;
          this.importError.set(
            response?.userMessage ??
              (response?.error as Partial<ApiError> | undefined)?.details?.join(' · ') ??
              (response?.error as Partial<ApiError> | undefined)?.message ??
              'Failed to import quest. Please try again.',
          );
        },
      });
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) {
      return `${bytes} B`;
    }
    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  upload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    this.api
      .import(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          input.value = '';
          this.refresh();
        },
        error: (err) => this.fail(err),
      });
  }

  issueSummary(book: BookSummary): string {
    return book.issues
      .filter((issue) => issue.severity === 'ERROR')
      .map((issue) => issue.message)
      .join(' · ');
  }

  formatBookTitle(bookId: string): string {
    const book = this.catalog().find((b) => b.id === bookId);
    if (book) {
      return this.bookMeta(book).displayTitle;
    }
    const match = BOOK_PRESENTATION[bookId];
    if (match?.displayTitle) {
      return match.displayTitle;
    }
    for (const [key, val] of Object.entries(BOOK_PRESENTATION)) {
      const slug = key.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
      const target = bookId.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
      if (slug === target || slug.replace(/^the-/, '') === target.replace(/^the-/, '')) {
        return val.displayTitle ?? key;
      }
    }
    return bookId
      .split(/[-_]/)
      .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
      .join(' ');
  }

  formatSaveDate(dateStr: string): string {
    if (!dateStr) {
      return '';
    }
    const date = new Date(dateStr);
    if (isNaN(date.getTime())) {
      return dateStr;
    }
    const datePart = date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
    const timePart = date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    });
    return `${datePart} · ${timePart}`;
  }

  bookMeta(book: BookSummary): BookPresentation {
    const match = BOOK_PRESENTATION[book.title] || BOOK_PRESENTATION[book.id];
    if (match) {
      return {
        displayTitle: match.displayTitle ?? book.title,
        displayAuthor: match.displayAuthor ?? book.author,
        summary:
          match.summary ??
          (book.playable
            ? 'A choice-driven adventure awaits. Every path holds its own surprises, dangers, and rewards.'
            : this.issueSummary(book)),
        difficulty:
          match.difficulty ??
          (book.difficulty === 'EASY'
            ? 'Easy'
            : book.difficulty === 'HARD'
              ? 'Hard'
              : 'Medium'),
        genre: match.genre ?? 'Adventure',
        readingTime: match.readingTime ?? '30-45 min',
        chapters: match.chapters ?? '10 chapters',
        tags: match.tags ?? this.bookTags(book),
      };
    }

    const capitalizedDifficulty: 'Easy' | 'Medium' | 'Hard' =
      book.difficulty === 'EASY'
        ? 'Easy'
        : book.difficulty === 'HARD'
          ? 'Hard'
          : 'Medium';

    return {
      displayTitle: book.title,
      displayAuthor: book.author,
      summary: book.playable
        ? 'A choice-driven adventure awaits. Every path holds its own surprises, dangers, and rewards.'
        : this.issueSummary(book),
      difficulty: capitalizedDifficulty,
      genre:
        book.difficulty === 'HARD'
          ? 'High Fantasy'
          : book.difficulty === 'EASY'
            ? 'Adventure'
            : 'Fantasy',
      readingTime: '30-45 min',
      chapters: '10 chapters',
      tags: this.bookTags(book),
    };
  }

  allBookKeywords(book: BookSummary): string[] {
    const meta = this.bookMeta(book);
    const tags = new Set<string>(meta.tags);
    tags.add(meta.genre);
    if (meta.genre === 'High Fantasy') {
      tags.add('Fantasy');
    }
    if (meta.genre === 'Steampunk Mystery') {
      tags.add('Mystery');
      tags.add('Steampunk');
    }
    for (const t of this.bookTags(book)) {
      tags.add(t);
    }
    return Array.from(tags);
  }

  bookTags(book: BookSummary): string[] {
    const catalog: Record<string, string[]> = {
      'The Crystal Caverns': ['Fantasy', 'Underground', 'Crystals', 'Magic'],
      'Pirates of the Jade Sea': ['Adventure', 'Pirates', 'Ocean', 'Treasure'],
      'The Clockwork Lighthouse': ['Mystery', 'Steampunk', 'Adventure', 'Steampunk Mystery'],
      "The Last Wizard's Tower": ['High Fantasy', 'Fantasy', 'Wizards', 'Magic', 'Tower'],
      'The Prisoner': ['Mystery', 'Fantasy', 'Escape', 'High Fantasy', 'Wizards', 'Magic', 'Tower'],
    };
    if (catalog[book.title]) {
      return catalog[book.title];
    }

    const tags = ['Adventure', 'Interactive'];
    if (book.difficulty === 'EASY') {
      tags.push('Fantasy');
    }
    if (book.difficulty === 'HARD') {
      tags.push('Mystery');
    }

    return tags;
  }

  private openGame(id: string): void {
    this.sessionStore.setActive(id);
    void this.router.navigate(['/game', id]);
  }

  private fail(err: unknown): void {
    const response = err as (HttpErrorResponse & { userMessage?: string }) | undefined;
    this.error.set(
      response?.userMessage ??
        (response?.error as Partial<ApiError> | undefined)?.details?.join(' · ') ??
        (response?.error as Partial<ApiError> | undefined)?.message ??
        'The request could not be completed. Please try again.',
    );
  }
}
