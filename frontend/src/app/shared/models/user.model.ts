export type UserRole = 'BOSS' | 'LIVREUR';

export interface User {
  id: string;
  livreurId?: string;
  firstName: string;
  lastName: string;
  birthDate?: string;
  email: string;
  phone?: string;
  role: UserRole;
  emailVerified: boolean;
  bossId?: string;
  active: boolean;
  createdAt: string;
}

export interface SignupRequest {
  livreurId?: string;
  firstName: string;
  lastName: string;
  birthDate: string;
  email: string;
  password: string;
  role: UserRole;
}

export interface SigninRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  refreshToken: string;
  user: User;
}
