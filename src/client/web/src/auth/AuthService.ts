/**
 * AuthService - Handles user authentication and registration
 *
 * This module communicates with the server's REST API for:
 * - User registration
 * - Login (getting session token)
 * - Session validation
 * - Password reset
 */

export interface RegisterRequest {
    username: string;
    email: string;
    password: string;
}

export interface LoginRequest {
    username: string;
    password: string;
    remember?: boolean;
}

export interface AuthResponse {
    success: boolean;
    message: string;
    token?: string;
    user?: UserInfo;
    errors?: Record<string, string>;
}

export interface UserInfo {
    id: string;
    username: string;
    email: string;
    rights: number;
    isMember: boolean;
    createdAt: string;
    lastLoginAt?: string;
}

export interface SessionInfo {
    valid: boolean;
    user?: UserInfo;
    expiresAt?: string;
}

// API base URL - can be configured based on environment
const API_BASE_URL = '/api/v1';

/**
 * Storage keys for auth data
 */
const STORAGE_KEYS = {
    TOKEN: 'rustscape_auth_token',
    USER: 'rustscape_user',
    REMEMBER: 'rustscape_remember',
};

/**
 * AuthService class for managing authentication
 */
export class AuthService {
    private token: string | null = null;
    private user: UserInfo | null = null;

    constructor() {
        this.loadFromStorage();
    }

    /**
     * Load auth data from localStorage
     */
    private loadFromStorage(): void {
        try {
            const storedToken = localStorage.getItem(STORAGE_KEYS.TOKEN);
            const storedUser = localStorage.getItem(STORAGE_KEYS.USER);

            if (storedToken) {
                this.token = storedToken;
            }

            if (storedUser) {
                this.user = JSON.parse(storedUser);
            }
        } catch (error) {
            console.warn('Failed to load auth data from storage:', error);
            this.clearStorage();
        }
    }

    /**
     * Save auth data to localStorage
     */
    private saveToStorage(remember: boolean = false): void {
        try {
            if (this.token) {
                localStorage.setItem(STORAGE_KEYS.TOKEN, this.token);
            }

            if (this.user) {
                localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(this.user));
            }

            localStorage.setItem(STORAGE_KEYS.REMEMBER, remember.toString());
        } catch (error) {
            console.warn('Failed to save auth data to storage:', error);
        }
    }

    /**
     * Clear auth data from localStorage
     */
    private clearStorage(): void {
        localStorage.removeItem(STORAGE_KEYS.TOKEN);
        localStorage.removeItem(STORAGE_KEYS.USER);
        localStorage.removeItem(STORAGE_KEYS.REMEMBER);
    }

    /**
     * Make an authenticated API request
     */
    private async apiRequest<T>(
        endpoint: string,
        method: 'GET' | 'POST' | 'PUT' | 'DELETE' = 'GET',
        body?: object
    ): Promise<T> {
        const headers: Record<string, string> = {
            'Content-Type': 'application/json',
        };

        if (this.token) {
            headers['Authorization'] = `Bearer ${this.token}`;
        }

        const response = await fetch(`${API_BASE_URL}${endpoint}`, {
            method,
            headers,
            body: body ? JSON.stringify(body) : undefined,
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new AuthError(
                errorData.message || `Request failed with status ${response.status}`,
                response.status,
                errorData.errors
            );
        }

        return response.json();
    }

    /**
     * Register a new user
     */
    async register(data: RegisterRequest): Promise<AuthResponse> {
        // Client-side validation
        const validationErrors = this.validateRegistration(data);
        if (Object.keys(validationErrors).length > 0) {
            return {
                success: false,
                message: 'Validation failed',
                errors: validationErrors,
            };
        }

        try {
            const response = await this.apiRequest<AuthResponse>('/auth/register', 'POST', {
                username: data.username.toLowerCase().trim(),
                email: data.email.toLowerCase().trim(),
                password: data.password,
            });

            if (response.success && response.token && response.user) {
                this.token = response.token;
                this.user = response.user;
                this.saveToStorage(false);
            }

            return response;
        } catch (error) {
            if (error instanceof AuthError) {
                return {
                    success: false,
                    message: error.message,
                    errors: error.errors,
                };
            }
            return {
                success: false,
                message: 'Registration failed. Please try again.',
            };
        }
    }

    /**
     * Login with username and password
     */
    async login(data: LoginRequest): Promise<AuthResponse> {
        // Client-side validation
        if (!data.username || !data.password) {
            return {
                success: false,
                message: 'Username and password are required',
            };
        }

        try {
            const response = await this.apiRequest<AuthResponse>('/auth/login', 'POST', {
                username: data.username.toLowerCase().trim(),
                password: data.password,
            });

            if (response.success && response.token && response.user) {
                this.token = response.token;
                this.user = response.user;
                this.saveToStorage(data.remember ?? false);
            }

            return response;
        } catch (error) {
            if (error instanceof AuthError) {
                return {
                    success: false,
                    message: error.message,
                };
            }
            return {
                success: false,
                message: 'Login failed. Please check your credentials.',
            };
        }
    }

    /**
     * Logout the current user
     */
    async logout(): Promise<void> {
        try {
            if (this.token) {
                await this.apiRequest('/auth/logout', 'POST');
            }
        } catch (error) {
            console.warn('Logout request failed:', error);
        } finally {
            this.token = null;
            this.user = null;
            this.clearStorage();
        }
    }

    /**
     * Validate the current session
     */
    async validateSession(): Promise<SessionInfo> {
        if (!this.token) {
            return { valid: false };
        }

        try {
            const response = await this.apiRequest<SessionInfo>('/auth/session', 'GET');

            if (response.valid && response.user) {
                this.user = response.user;
                this.saveToStorage(localStorage.getItem(STORAGE_KEYS.REMEMBER) === 'true');
            } else {
                this.clearStorage();
                this.token = null;
                this.user = null;
            }

            return response;
        } catch (error) {
            this.clearStorage();
            this.token = null;
            this.user = null;
            return { valid: false };
        }
    }

    /**
     * Check if username is available
     */
    async checkUsername(username: string): Promise<{ available: boolean; message?: string }> {
        if (!username || username.length < 1 || username.length > 12) {
            return { available: false, message: 'Username must be 1-12 characters' };
        }

        if (!/^[a-zA-Z0-9_]+$/.test(username)) {
            return { available: false, message: 'Username can only contain letters, numbers, and underscores' };
        }

        try {
            const response = await this.apiRequest<{ available: boolean }>(
                `/auth/check-username?username=${encodeURIComponent(username)}`,
                'GET'
            );
            return response;
        } catch (error) {
            return { available: false, message: 'Could not check username availability' };
        }
    }

    /**
     * Request password reset
     */
    async requestPasswordReset(email: string): Promise<AuthResponse> {
        try {
            return await this.apiRequest<AuthResponse>('/auth/forgot-password', 'POST', { email });
        } catch (error) {
            return {
                success: false,
                message: 'Could not send password reset email. Please try again.',
            };
        }
    }

    /**
     * Reset password with token
     */
    async resetPassword(token: string, newPassword: string): Promise<AuthResponse> {
        if (newPassword.length < 6) {
            return {
                success: false,
                message: 'Password must be at least 6 characters',
            };
        }

        try {
            return await this.apiRequest<AuthResponse>('/auth/reset-password', 'POST', {
                token,
                password: newPassword,
            });
        } catch (error) {
            return {
                success: false,
                message: 'Could not reset password. The link may have expired.',
            };
        }
    }

    /**
     * Validate registration data
     */
    private validateRegistration(data: RegisterRequest): Record<string, string> {
        const errors: Record<string, string> = {};

        // Username validation
        if (!data.username) {
            errors.username = 'Username is required';
        } else if (data.username.length < 1 || data.username.length > 12) {
            errors.username = 'Username must be 1-12 characters';
        } else if (!/^[a-zA-Z0-9_]+$/.test(data.username)) {
            errors.username = 'Username can only contain letters, numbers, and underscores';
        }

        // Email validation
        if (!data.email) {
            errors.email = 'Email is required';
        } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email)) {
            errors.email = 'Please enter a valid email address';
        }

        // Password validation
        if (!data.password) {
            errors.password = 'Password is required';
        } else if (data.password.length < 6) {
            errors.password = 'Password must be at least 6 characters';
        }

        return errors;
    }

    /**
     * Get current auth token
     */
    getToken(): string | null {
        return this.token;
    }

    /**
     * Get current user info
     */
    getUser(): UserInfo | null {
        return this.user;
    }

    /**
     * Check if user is logged in
     */
    isLoggedIn(): boolean {
        return this.token !== null && this.user !== null;
    }

    /**
     * Check if user is admin
     */
    isAdmin(): boolean {
        return this.user?.rights === 2;
    }

    /**
     * Check if user is moderator or higher
     */
    isModerator(): boolean {
        return (this.user?.rights ?? 0) >= 1;
    }

    /**
     * Get password strength (0-3)
     */
    static getPasswordStrength(password: string): { score: number; label: string } {
        if (!password) {
            return { score: 0, label: '' };
        }

        let score = 0;

        // Length check
        if (password.length >= 6) score++;
        if (password.length >= 10) score++;

        // Complexity checks
        if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score++;
        if (/\d/.test(password)) score++;
        if (/[^a-zA-Z0-9]/.test(password)) score++;

        // Normalize to 0-3
        const normalizedScore = Math.min(3, Math.floor(score / 2));

        const labels = ['', 'Weak', 'Medium', 'Strong'];
        return { score: normalizedScore, label: labels[normalizedScore] };
    }
}

/**
 * Custom error class for auth errors
 */
export class AuthError extends Error {
    status: number;
    errors?: Record<string, string>;

    constructor(message: string, status: number, errors?: Record<string, string>) {
        super(message);
        this.name = 'AuthError';
        this.status = status;
        this.errors = errors;
    }
}

// Export singleton instance
export const authService = new AuthService();

export default authService;
