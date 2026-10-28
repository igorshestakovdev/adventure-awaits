import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/library/library.page').then((m) => m.LibraryPage),
    title: 'Adventure Library',
  },
  {
    path: 'game/:sessionId',
    loadComponent: () => import('./features/game/game.page').then((m) => m.GamePage),
    title: 'Your Adventure',
  },
  { path: '**', redirectTo: '' },
];
