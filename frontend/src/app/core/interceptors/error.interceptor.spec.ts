import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AdventureApi } from '../api/adventure.api';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let api: AdventureApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    api = TestBed.inject(AdventureApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => { http.verify(); });

  it('attaches userMessage from body.message on 4xx errors', () => {
    let captured: unknown;
    api.books().subscribe({ error: (e) => { captured = e; } });

    http.expectOne('/api/books').flush(
      { message: 'Not found', details: [] },
      { status: 404, statusText: 'Not Found' },
    );

    expect((captured as { userMessage: string }).userMessage).toBe('Not found');
  });

  it('joins details array when present', () => {
    let captured: unknown;
    api.books().subscribe({ error: (e) => { captured = e; } });

    http.expectOne('/api/books').flush(
      { message: 'Validation failed', details: ['Field A is required', 'Field B is invalid'] },
      { status: 400, statusText: 'Bad Request' },
    );

    expect((captured as { userMessage: string }).userMessage).toBe('Field A is required · Field B is invalid');
  });

  it('uses fallback message when body is empty', () => {
    let captured: unknown;
    api.books().subscribe({ error: (e) => { captured = e; } });

    http.expectOne('/api/books').flush(null, { status: 500, statusText: 'Internal Server Error' });

    expect((captured as { userMessage: string }).userMessage).toBe(
      'The request could not be completed. Please try again.',
    );
  });

  it('passes successful responses through unchanged', () => {
    let result: unknown;
    api.books().subscribe({ next: (v) => { result = v; } });

    http.expectOne('/api/books').flush([{ id: '1', title: 'Book' }]);

    expect(result).toEqual([{ id: '1', title: 'Book' }]);
  });
});
