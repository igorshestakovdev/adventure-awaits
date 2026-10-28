import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BookSummary, GameState, SaveSummary } from './models';

@Injectable({ providedIn: 'root' })
export class AdventureApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  books(query = '', difficulty = ''): Observable<BookSummary[]> {
    let params = new HttpParams();
    if (query) { params = params.set('q', query); }
    if (difficulty) { params = params.set('difficulty', difficulty); }
    return this.http.get<BookSummary[]>(`${this.base}/books`, { params });
  }

  start(bookId: string): Observable<GameState> {
    return this.http.post<GameState>(`${this.base}/games`, { bookId });
  }

  game(sessionId: string): Observable<GameState> {
    return this.http.get<GameState>(`${this.base}/games/${sessionId}`);
  }

  choose(sessionId: string, optionId: string, expectedRevision: number, requestId: string): Observable<GameState> {
    return this.http.post<GameState>(`${this.base}/games/${sessionId}/choices`, { optionId, expectedRevision, requestId });
  }

  save(sessionId: string, name: string): Observable<SaveSummary> {
    return this.http.post<SaveSummary>(`${this.base}/saves`, { sessionId, name });
  }

  saves(): Observable<SaveSummary[]> {
    return this.http.get<SaveSummary[]>(`${this.base}/saves`);
  }

  restore(saveId: string): Observable<GameState> {
    return this.http.post<GameState>(`${this.base}/saves/${saveId}/restore`, {});
  }

  deleteSave(saveId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/saves/${saveId}`);
  }

  import(file: File): Observable<BookSummary> {
    const data = new FormData();
    data.append('file', file);
    return this.http.post<BookSummary>(`${this.base}/books/import`, data);
  }
}
