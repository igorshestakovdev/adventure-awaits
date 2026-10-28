export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type GameStatus = 'ACTIVE' | 'COMPLETED' | 'DEAD';

export interface ValidationIssue {
  severity: 'ERROR' | 'WARNING';
  code: string;
  message: string;
}

export interface BookSummary {
  id: string;
  title: string;
  author: string;
  difficulty: Difficulty;
  playable: boolean;
  issues: ValidationIssue[];
}

export interface Choice {
  id: string;
  description: string;
}

export interface ConsequenceResult {
  text: string;
  healthDelta: number;
}

export interface GameState {
  sessionId: string;
  bookId: string;
  bookTitle: string;
  sectionId: number;
  text: string;
  choices: Choice[];
  health: number;
  status: GameStatus;
  revision: number;
  consequence: ConsequenceResult | null;
}

export interface SaveSummary {
  id: string;
  name: string;
  bookId: string;
  savedAt: string;
}

export interface ApiError {
  code: string;
  message: string;
  status: number;
  details: string[];
}
