import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AdventureApi } from './adventure.api';

describe('AdventureApi HTTP contract', () => {
  let api: AdventureApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AdventureApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('omits empty filters and sends supplied search filters', () => {
    api.books().subscribe(value => expect(value).toEqual([]));
    const empty = http.expectOne('/api/books');
    expect(empty.request.method).toBe('GET');
    expect(empty.request.params.keys()).toEqual([]);
    empty.flush([]);

    api.books('sea & sky', 'HARD').subscribe();
    const filtered = http.expectOne(req => req.url === '/api/books');
    expect(filtered.request.params.get('q')).toBe('sea & sky');
    expect(filtered.request.params.get('difficulty')).toBe('HARD');
    filtered.flush([]);
  });

  it('starts and loads the server session', () => {
    api.start('book-1').subscribe();
    const start = http.expectOne('/api/games');
    expect(start.request.method).toBe('POST');
    expect(start.request.body).toEqual({ bookId: 'book-1' });
    start.flush({ sessionId: 'session-1' });

    api.game('session-1').subscribe(value => expect(value.revision).toBe(42));
    const game = http.expectOne('/api/games/session-1');
    expect(game.request.method).toBe('GET');
    game.flush({ revision: 42 });
  });

  it('sends revision and request identity unchanged', () => {
    api.choose('session-1', 'option-2', 42, 'request-3').subscribe();
    const choice = http.expectOne('/api/games/session-1/choices');
    expect(choice.request.method).toBe('POST');
    expect(choice.request.body).toEqual({ optionId: 'option-2', expectedRevision: 42, requestId: 'request-3' });
    choice.flush({});
  });

  it('creates, lists, restores and deletes saves', () => {
    api.save('session-1', 'Checkpoint').subscribe();
    const save = http.expectOne('/api/saves');
    expect(save.request.method).toBe('POST');
    expect(save.request.body).toEqual({ sessionId: 'session-1', name: 'Checkpoint' });
    save.flush({});

    api.saves().subscribe(value => expect(value).toEqual([]));
    const list = http.expectOne('/api/saves');
    expect(list.request.method).toBe('GET');
    list.flush([]);

    api.restore('save-1').subscribe();
    const restore = http.expectOne('/api/saves/save-1/restore');
    expect(restore.request.method).toBe('POST');
    expect(restore.request.body).toEqual({});
    restore.flush({});

    api.deleteSave('save-1').subscribe();
    const del = http.expectOne('/api/saves/save-1');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
  });

  it('uploads the selected file as multipart data', () => {
    const file = new File(['{}'], 'book.json', { type: 'application/json' });
    api.import(file).subscribe();
    const upload = http.expectOne('/api/books/import');
    expect(upload.request.method).toBe('POST');
    expect(upload.request.body.get('file')).toBe(file);
    upload.flush({});
  });

  it('preserves the conflict response for the caller', () => {
    api.choose('session', 'option', 0, 'request').subscribe({
      error: error => {
        expect(error.status).toBe(409);
        expect(error.error).toEqual({ sessionId: 'session', revision: 1 });
      },
    });
    http.expectOne('/api/games/session/choices').flush({ sessionId: 'session', revision: 1 }, { status: 409, statusText: 'Conflict' });
  });
});
