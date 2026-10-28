import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AdventureApi } from '../../core/api/adventure.api';
import { BookSummary, GameState } from '../../core/api/models';
import { LibraryPage } from './library.page';

const valid: BookSummary = {
  id: 'valid',
  title: 'Valid Story',
  author: 'Author',
  difficulty: 'EASY',
  playable: true,
  issues: [],
};

const invalid: BookSummary = {
  id: 'invalid',
  title: 'Broken Story',
  author: 'Author',
  difficulty: 'HARD',
  playable: false,
  issues: [{ severity: 'ERROR', code: 'NO_END', message: 'No ending' }],
};

describe('LibraryPage', () => {
  const createComponent = async (overrides: Record<string, unknown> = {}) => {
    const api = {
      books: vi.fn(() => of([valid])),
      saves: vi.fn(() => of([])),
      start: vi.fn(() => of({ sessionId: 'new-session' })),
      restore: vi.fn(() => of({ sessionId: 'restored-session' })),
      deleteSave: vi.fn(() => of(undefined)),
      import: vi.fn(() => of(valid)),
      ...overrides,
    };
    await TestBed.configureTestingModule({
      imports: [LibraryPage],
      providers: [provideRouter([]), { provide: AdventureApi, useValue: api }],
    }).compileComponents();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LibraryPage);

    return { component: fixture.componentInstance, api, navigate };
  };

  it('starts only playable books and opens the returned session', async () => {
    const { component, api, navigate } = await createComponent();
    component.start(invalid);
    expect(api.start).not.toHaveBeenCalled();

    component.start(valid);

    expect(navigate).toHaveBeenCalledWith(['/game', 'new-session']);
    expect(localStorage.getItem('adventure.activeSession')).toBe('new-session');
    expect(component.busyBook()).toBeNull();
  });

  it('restores a save into the session returned by the server', async () => {
    const { component, api, navigate } = await createComponent();

    component.restore({ id: 'save-1', name: 'Checkpoint', bookId: valid.id, savedAt: 'now' });

    expect(api.restore).toHaveBeenCalledWith('save-1');
    expect(navigate).toHaveBeenCalledWith(['/game', 'restored-session']);
  });

  it('imports a selected file and reloads the catalog', async () => {
    const { component, api } = await createComponent();
    component.upload({ target: { files: [] } } as unknown as Event);
    expect(api.import).not.toHaveBeenCalled();
    const file = new File(['{}'], 'story.json');
    const input = { files: [file], value: 'story.json' };

    component.upload({ target: input } as unknown as Event);

    expect(api.import).toHaveBeenCalledWith(file);
    expect(input.value).toBe('');
    expect(api.books).toHaveBeenCalledTimes(2);
  });

  it('shows import diagnostics and tolerates an unavailable save list', async () => {
    const { component } = await createComponent({
      saves: vi.fn(() => throwError(() => new HttpErrorResponse({ status: 500 }))),
      import: vi.fn(() => throwError(() => new HttpErrorResponse({ status: 422, error: { details: ['NO_END', 'INVALID_TARGET'] } }))),
    });
    expect(component.books()).toEqual([valid]);
    expect(component.saves()).toEqual([]);

    component.upload({ target: { files: [new File(['{}'], 'bad.json')] } } as unknown as Event);

    expect(component.error()).toBe('NO_END · INVALID_TARGET');
  });

  it('shows validation failures and disables an invalid book', async () => {
    const api = {
      books: vi.fn(() => of([valid, invalid])),
      saves: vi.fn(() => of([])),
    };

    await TestBed.configureTestingModule({
      imports: [LibraryPage],
      providers: [provideRouter([]), { provide: AdventureApi, useValue: api }],
    }).compileComponents();

    const fixture = TestBed.createComponent(LibraryPage);
    fixture.detectChanges();
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('article button'),
    ) as HTMLButtonElement[];

    expect(buttons[1].disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('No ending');
  });

  it('blocks a repeated start while the first request is pending', async () => {
    const pending = new Subject<GameState>();
    const api = {
      books: vi.fn(() => of([valid])),
      saves: vi.fn(() => of([])),
      start: vi.fn(() => pending),
    };

    await TestBed.configureTestingModule({
      imports: [LibraryPage],
      providers: [provideRouter([]), { provide: AdventureApi, useValue: api }],
    }).compileComponents();

    const fixture = TestBed.createComponent(LibraryPage);
    fixture.detectChanges();

    fixture.componentInstance.start(valid);
    fixture.componentInstance.start(valid);

    expect(api.start).toHaveBeenCalledTimes(1);
  });

  it('filters the loaded catalog without more API requests', async () => {
    const api = {
      books: vi.fn(() => of([valid, invalid])),
      saves: vi.fn(() => of([])),
    };

    await TestBed.configureTestingModule({
      imports: [LibraryPage],
      providers: [provideRouter([]), { provide: AdventureApi, useValue: api }],
    }).compileComponents();

    const fixture = TestBed.createComponent(LibraryPage);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.query.set(' AUTHOR ');
    expect(component.books()).toEqual([valid, invalid]);

    component.difficulty.set('EASY');
    component.genre.set('Fantasy');
    expect(component.books()).toEqual([valid]);

    component.query.set('broken');
    expect(component.books()).toEqual([]);

    component.difficulty.set('HARD');
    component.genre.set('Mystery');
    expect(component.books()).toEqual([invalid]);
    expect(api.books).toHaveBeenCalledTimes(1);
  });

  it('toggles filters on and off and does not have an All button', async () => {
    const api = {
      books: vi.fn(() => of([valid, invalid])),
      saves: vi.fn(() => of([])),
    };

    await TestBed.configureTestingModule({
      imports: [LibraryPage],
      providers: [provideRouter([]), { provide: AdventureApi, useValue: api }],
    }).compileComponents();

    const fixture = TestBed.createComponent(LibraryPage);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('.filter-row button'),
    ) as HTMLButtonElement[];

    const allButtonsText = buttons.map((b) => b.textContent?.trim().toLowerCase());
    expect(allButtonsText).not.toContain('all');

    component.toggleGenre('Fantasy');
    expect(component.genre()).toBe('Fantasy');

    component.toggleGenre('Fantasy');
    expect(component.genre()).toBe('');

    component.toggleDifficulty('EASY');
    expect(component.difficulty()).toBe('EASY');

    component.toggleDifficulty('EASY');
    expect(component.difficulty()).toBe('');
  });

  it('can refresh after a failed load and keeps the selected filters', async () => {
    const api = {
      books: vi
        .fn()
        .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })))
        .mockReturnValueOnce(of([valid, invalid])),
      saves: vi.fn(() => of([])),
    };

    await TestBed.configureTestingModule({
      imports: [LibraryPage],
      providers: [provideRouter([]), { provide: AdventureApi, useValue: api }],
    }).compileComponents();

    const fixture = TestBed.createComponent(LibraryPage);
    const component = fixture.componentInstance;
    expect(component.error()).not.toBe('');
    expect(component.loading()).toBe(false);

    component.query.set('valid');
    component.refresh();

    expect(component.books()).toEqual([valid]);
    expect(component.error()).toBe('');
    expect(component.loading()).toBe(false);
  });

  it('formats save book titles and timestamps elegantly', async () => {
    const { component } = await createComponent();

    expect(component.formatBookTitle('valid')).toBe('Valid Story');
    expect(component.formatBookTitle('clockwork-lighthouse')).toBe('The Clockwork Lighthouse');
    expect(component.formatBookTitle('unknown-ancient-tome')).toBe('Unknown Ancient Tome');

    expect(component.formatSaveDate('')).toBe('');
    expect(component.formatSaveDate('now')).toBe('now');
    const formatted = component.formatSaveDate('2026-10-28T07:00:05.925686+03:00');
    expect(formatted).toContain('2026');
    expect(formatted).toContain('·');
  });

  it('renders saved adventures with stylish card structure', async () => {
    const api = {
      books: vi.fn(() => of([valid])),
      saves: vi.fn(() =>
        of([
          {
            id: 'save-101',
            name: 'Into the Depths',
            bookId: 'valid',
            savedAt: '2026-10-28T07:00:05.925686+03:00',
          },
        ])
      ),
    };

    await TestBed.configureTestingModule({
      imports: [LibraryPage],
      providers: [provideRouter([]), { provide: AdventureApi, useValue: api }],
    }).compileComponents();

    const fixture = TestBed.createComponent(LibraryPage);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const saveCard = el.querySelector('.save-card');
    expect(saveCard).toBeTruthy();
    expect(el.querySelector('.saved__eyebrow')?.textContent).toContain('SAVED CHRONICLES');
    expect(el.querySelector('.saved__count')?.textContent).toContain('1 bookmark');
    expect(saveCard?.querySelector('.save-card__name')?.textContent).toContain('Into the Depths');
    expect(saveCard?.querySelector('.save-card__book')?.textContent).toContain('Valid Story');
  });

  it('confirms and deletes a save successfully', async () => {
    const saveItem = {
      id: 'save-101',
      name: 'Into the Depths',
      bookId: 'valid',
      savedAt: 'now',
    };
    const { component, api } = await createComponent({
      saves: vi.fn(() => of([saveItem])),
      deleteSave: vi.fn(() => of(undefined)),
    });

    const event = new MouseEvent('click');
    component.promptDelete(saveItem, event);
    expect(component.confirmingDeleteId()).toBe('save-101');

    component.cancelDelete(event);
    expect(component.confirmingDeleteId()).toBeNull();

    component.promptDelete(saveItem, event);
    component.confirmDelete(saveItem, event);

    expect(api.deleteSave).toHaveBeenCalledWith('save-101');
    expect(component.saves()).toEqual([]);
    expect(component.confirmingDeleteId()).toBeNull();
    expect(component.activeSession()).toBeNull();
  });

  it('dismisses active adventure banner', async () => {
    localStorage.setItem('adventure.activeSession', 'session-active');
    const saveItem = { id: 'save-1', name: 'Checkpoint', bookId: 'valid', savedAt: 'now' };
    const { component } = await createComponent({ saves: vi.fn(() => of([saveItem])) });
    expect(component.activeSession()).toBe('session-active');
    expect(component.hasActiveAdventure()).toBe(true);

    component.dismissActiveSession();
    expect(component.activeSession()).toBeNull();
    expect(component.hasActiveAdventure()).toBe(false);
    expect(localStorage.getItem('adventure.activeSession')).toBeNull();
  });

  it('does not show active adventure banner when there are no saves', async () => {
    localStorage.setItem('adventure.activeSession', 'session-active');
    const { component } = await createComponent({ saves: vi.fn(() => of([])) });

    expect(component.hasActiveAdventure()).toBe(false);
    expect(component.activeSession()).toBeNull();
  });

  it('handles delete failure gracefully', async () => {
    const saveItem = {
      id: 'save-102',
      name: 'Failing Save',
      bookId: 'valid',
      savedAt: 'now',
    };
    const { component, api } = await createComponent({
      saves: vi.fn(() => of([saveItem])),
      deleteSave: vi.fn(() =>
        throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'Database error' } }))
      ),
    });

    const event = new MouseEvent('click');
    component.confirmDelete(saveItem, event);

    expect(api.deleteSave).toHaveBeenCalledWith('save-102');
    expect(component.saves()).toEqual([saveItem]);
    expect(component.error()).toBe('Database error');
    expect(component.busyDelete()).toBeNull();
  });

  describe('Quest Import Modal and Immediate Validation', () => {
    const validQuestJson = JSON.stringify({
      title: 'The Lost Crypt',
      author: 'Eldred the Bold',
      difficulty: 'MEDIUM',
      sections: [
        {
          id: 1,
          text: 'You stand before the ancient crypt.',
          type: 'BEGIN',
          options: [{ description: 'Open the door', gotoId: 2 }],
        },
        {
          id: 2,
          text: 'You entered the sanctuary and found peace.',
          type: 'END',
          options: [],
        },
      ],
    });

    it('opens, closes, and resets the import modal', async () => {
      const { component } = await createComponent();
      expect(component.isImportModalOpen()).toBe(false);

      component.openImportModal();
      expect(component.isImportModalOpen()).toBe(true);
      expect(component.selectedFile()).toBeNull();
      expect(component.isValidFile()).toBe(false);

      component.closeImportModal();
      expect(component.isImportModalOpen()).toBe(false);

      component.openImportModal();
      component.onEscape();
      expect(component.isImportModalOpen()).toBe(false);
    });

    it('resets state when file input event has no files', async () => {
      const { component } = await createComponent();
      component.openImportModal();
      await component.onFileSelected({ target: { files: null } } as unknown as Event);
      expect(component.selectedFile()).toBeNull();
      expect(component.isValidFile()).toBe(false);
    });

    it('rejects an empty file or invalid JSON with immediate error', async () => {
      const { component } = await createComponent();
      component.openImportModal();

      const emptyFile = new File([''], 'empty.json', { type: 'application/json' });
      await component.onFileSelected({ target: { files: [emptyFile] } } as unknown as Event);
      expect(component.isValidFile()).toBe(false);
      expect(component.validationErrors()).toContain('File is empty');

      const syntaxErrFile = new File(['{ broken: json'], 'syntax.json', { type: 'application/json' });
      await component.onFileSelected({ target: { files: [syntaxErrFile] } } as unknown as Event);
      expect(component.isValidFile()).toBe(false);
      expect(component.validationErrors()).toContain('Invalid JSON syntax');

      const arrayFile = new File(['[1, 2, 3]'], 'array.json', { type: 'application/json' });
      await component.onFileSelected({ target: { files: [arrayFile] } } as unknown as Event);
      expect(component.isValidFile()).toBe(false);
      expect(component.validationErrors()).toContain('Quest file must be a JSON object');
    });

    it('rejects files with missing metadata or bad difficulty', async () => {
      const { component } = await createComponent();
      component.openImportModal();

      const badMetaJson = JSON.stringify({
        title: '',
        author: '   ',
        difficulty: 'SUPER_HARD',
        sections: [],
      });
      const file = new File([badMetaJson], 'bad-meta.json', { type: 'application/json' });
      await component.onFileSelected({ target: { files: [file] } } as unknown as Event);

      expect(component.isValidFile()).toBe(false);
      expect(component.validationErrors()).toContain('Book title is required');
      expect(component.validationErrors()).toContain('Author is required');
      expect(component.validationErrors()).toContain('Difficulty must be EASY, MEDIUM, or HARD');
      expect(component.validationErrors()).toContain('Book must contain sections');
    });

    it('rejects quest files with invalid story graph or missing targets', async () => {
      const { component } = await createComponent();
      component.openImportModal();

      const brokenGraphJson = JSON.stringify({
        title: 'Broken Quest',
        author: 'Dark Knight',
        difficulty: 'HARD',
        sections: [
          {
            id: 1,
            text: 'Starting here',
            type: 'BEGIN',
            options: [{ description: 'Go to missing section', gotoId: 999 }],
          },
          {
            id: 2,
            text: 'Non ending section without options',
            type: 'NODE',
            options: [],
          },
        ],
      });
      const file = new File([brokenGraphJson], 'broken.json', { type: 'application/json' });
      await component.onFileSelected({ target: { files: [file] } } as unknown as Event);

      expect(component.isValidFile()).toBe(false);
      expect(component.validationErrors()).toContain('Book must contain at least one END section');
      expect(component.validationErrors()).toContain('Non-ending section 2 has no options');
      expect(component.validationErrors()).toContain('Section 1 points to missing section 999');
    });

    it('rejects files with multiple BEGIN sections or duplicate section IDs', async () => {
      const { component } = await createComponent();
      component.openImportModal();

      const duplicateJson = JSON.stringify({
        title: 'Duplicate Quest',
        author: 'Tester',
        difficulty: 'EASY',
        sections: [
          { id: 1, text: 'First begin', type: 'BEGIN', options: [{ description: 'Go', gotoId: 1 }] },
          { id: 1, text: 'Second begin', type: 'BEGIN', options: [{ description: 'Go', gotoId: 1 }] },
          { id: 2, text: 'Ending', type: 'END', options: [] },
        ],
      });
      const file = new File([duplicateJson], 'dup.json', { type: 'application/json' });
      await component.onFileSelected({ target: { files: [file] } } as unknown as Event);

      expect(component.isValidFile()).toBe(false);
      expect(component.validationErrors()).toContain('Duplicate section id: 1');
      expect(component.validationErrors()).toContain('Book must contain exactly one BEGIN section; found 2');
    });

    it('rejects malformed section elements and invalid options', async () => {
      const { component } = await createComponent();
      const malformedJson = JSON.stringify({
        title: 'Malformed Quest',
        author: 'Tester',
        difficulty: 'EASY',
        sections: [
          null,
          { id: 'not-a-number', text: '', type: 'INVALID_TYPE', options: null },
          {
            id: 1,
            text: 'Begin section',
            type: 'BEGIN',
            options: [null, { description: '', gotoId: 2 }],
          },
          { id: 2, text: 'End section', type: 'END' },
        ],
      });
      const file = new File([malformedJson], 'malformed.json', { type: 'application/json' });
      const res = component.validateQuestJson(file, malformedJson);
      expect(res.valid).toBe(false);
      expect(res.errors).toContain('Section at index 0 is not an object');
      expect(res.errors).toContain('Section at index 1 has an invalid or missing numeric ID');
      expect(res.errors).toContain('Option 1 in section 1 is invalid');
      expect(res.errors).toContain('Option 2 in section 1 is missing a description');
    });

    it('rejects files exceeding 1 MB limit', async () => {
      const { component } = await createComponent();
      const largeFile = new File(['dummy'], 'large.json', { type: 'application/json' });
      Object.defineProperty(largeFile, 'size', { value: 1_000_001 });

      const res = component.validateQuestJson(largeFile, validQuestJson);
      expect(res.valid).toBe(false);
      expect(res.errors).toContain('File size exceeds 1 MB limit');
    });

    it('blocks upload when file is invalid and submitImport is called', async () => {
      const { component, api } = await createComponent();
      component.openImportModal();

      const file = new File(['{}'], 'invalid.json');
      await component.onFileSelected({ target: { files: [file] } } as unknown as Event);
      expect(component.isValidFile()).toBe(false);

      component.submitImport();
      expect(api.import).not.toHaveBeenCalled();
    });

    it('validates a correct quest file immediately and enables upload', async () => {
      const { component, api } = await createComponent();
      component.openImportModal();

      const validFile = new File([validQuestJson], 'valid-quest.json', { type: 'application/json' });
      await component.onFileSelected({ target: { files: [validFile] } } as unknown as Event);

      expect(component.isValidFile()).toBe(true);
      expect(component.validationErrors()).toEqual([]);
      expect(component.validationSuccess()).toContain('The Lost Crypt');
      expect(component.validationSuccess()).toContain('Eldred the Bold');

      component.submitImport();

      expect(api.import).toHaveBeenCalledWith(validFile);
      expect(component.isImportModalOpen()).toBe(false);
      expect(api.books).toHaveBeenCalledTimes(2);
    });

    it('displays error in modal when upload request fails on server', async () => {
      const { component, api } = await createComponent({
        import: vi.fn(() =>
          throwError(
            () =>
              new HttpErrorResponse({
                status: 422,
                error: { message: 'Server rejected the quest file' },
              }),
          ),
        ),
      });
      component.openImportModal();

      const validFile = new File([validQuestJson], 'quest.json', { type: 'application/json' });
      await component.onFileSelected({ target: { files: [validFile] } } as unknown as Event);
      expect(component.isValidFile()).toBe(true);

      component.submitImport();

      expect(api.import).toHaveBeenCalledWith(validFile);
      expect(component.isImportModalOpen()).toBe(true);
      expect(component.importError()).toBe('Server rejected the quest file');
      expect(component.importing()).toBe(false);
    });

    it('formats file sizes accurately', async () => {
      const { component } = await createComponent();
      expect(component.formatFileSize(450)).toBe('450 B');
      expect(component.formatFileSize(2048)).toBe('2.0 KB');
      expect(component.formatFileSize(2 * 1024 * 1024)).toBe('2.0 MB');
    });

    it('extracts keywords and tags correctly for special books', async () => {
      const { component } = await createComponent();
      const steampunkBook: BookSummary = {
        id: 'clockwork',
        title: 'The Clockwork Lighthouse',
        author: 'Barnaby Quill',
        difficulty: 'EASY',
        playable: true,
        issues: [],
      };
      const keywords = component.allBookKeywords(steampunkBook);
      expect(keywords).toContain('Steampunk');
      expect(keywords).toContain('Mystery');

      const cavernsBook: BookSummary = {
        id: 'crystal',
        title: 'The Crystal Caverns',
        author: 'Elena Brightwater',
        difficulty: 'MEDIUM',
        playable: true,
        issues: [],
      };
      const cavernTags = component.bookTags(cavernsBook);
      expect(cavernTags).toContain('Crystals');
    });

    it('renders modal markup and disables submit button when invalid', async () => {
      await TestBed.configureTestingModule({
        imports: [LibraryPage],
        providers: [
          provideRouter([]),
          {
            provide: AdventureApi,
            useValue: {
              books: vi.fn(() => of([valid])),
              saves: vi.fn(() => of([])),
            },
          },
        ],
      }).compileComponents();

      const fixture = TestBed.createComponent(LibraryPage);
      fixture.detectChanges();

      const addBtn = fixture.nativeElement.querySelector('.btn-add-quest') as HTMLButtonElement;
      expect(addBtn).toBeTruthy();
      expect(addBtn.textContent).toContain('Add quest');

      addBtn.click();
      fixture.detectChanges();

      const modal = fixture.nativeElement.querySelector('.modal-card');
      expect(modal).toBeTruthy();
      expect(modal.querySelector('#modal-title')?.textContent).toContain('Add New Quest');
      expect(modal.querySelector('.modal-desc')).toBeTruthy();

      const submitBtn = modal.querySelector('.btn-submit') as HTMLButtonElement;
      expect(submitBtn).toBeTruthy();
      expect(submitBtn.disabled).toBe(true);

      expect(modal.querySelector('.example-textarea')).toBeNull();
      const toggleBtn = modal.querySelector('.btn-toggle-example') as HTMLButtonElement;
      toggleBtn.click();
      fixture.detectChanges();

      const textarea = modal.querySelector('.example-textarea') as HTMLTextAreaElement;
      expect(textarea).toBeTruthy();
      expect(textarea.value).toContain('The Whispering Tombs');
    });

    it('toggles, uses, and copies the example quest', async () => {
      const { component } = await createComponent();
      component.openImportModal();

      expect(component.showExample()).toBe(false);
      component.toggleExample();
      expect(component.showExample()).toBe(true);
      component.toggleExample();
      expect(component.showExample()).toBe(false);

      component.useExampleQuest();
      expect(component.isValidFile()).toBe(true);
      expect(component.selectedFile()?.name).toBe('sample-quest.json');
      expect(component.validationSuccess()).toContain('The Whispering Tombs');

      const writeTextMock = vi.fn().mockResolvedValue(undefined);
      Object.assign(navigator, {
        clipboard: {
          writeText: writeTextMock,
        },
      });

      component.copyExampleQuest();
      expect(writeTextMock).toHaveBeenCalledWith(component.exampleQuestJson);
      expect(component.copiedExample()).toBe(true);
    });
  });
});
