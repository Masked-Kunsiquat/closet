import { sha256 } from "@oslojs/crypto/sha2";
import { encodeBase32LowerCaseNoPadding, encodeHexLowerCase } from "@oslojs/encoding";
import { API_URL } from "./config";
import axios from "axios";
import { UserSessionResponse } from "../types";


/**
 * Generate a secure session token.
 * @returns A Base32-encoded token.
 */
export function generateSessionToken(): string {
    const bytes = new Uint8Array(20);
    crypto.getRandomValues(bytes);
    return encodeBase32LowerCaseNoPadding(bytes);
}

/**
 * Validate the user's session.
 * @returns User ID if valid session, null if not authenticated.
 */
export async function validateSession(): Promise<UserSessionResponse> {
    try {
        // Parse cookies to get the session token
        const cookies = document.cookie.split("; ").reduce((acc, cookie) => {
            const [name, value] = cookie.split("=");
            acc[name] = value;
            return acc;
        }, {} as Record<string, string>);

        console.log("Session token from cookies:", cookies.session_token);

        if (!cookies.session_token) return null;

        // Hash the session token before checking the database
        const sessionId = encodeHexLowerCase(sha256(new TextEncoder().encode(cookies.session_token)));

        console.log("Hashed session token:", sessionId);

        // Fetch session from PostgREST
        const { data } = await axios.get(
            `${API_URL}/user_sessions?id=eq.${sessionId}&select=user_id,expires_at`
        );

        console.log("Session data from PostgREST:", data);

        if (data.length === 0) return null;

        const session = data[0];

        // If session is expired, delete it
        if (Date.now() >= new Date(session.expires_at).getTime()) {
            await axios.delete(`${API_URL}/user_sessions?id=eq.${sessionId}`);
            return null;
        }

        // If session is close to expiration, auto-renew it
        if (Date.now() >= new Date(session.expires_at).getTime() - 1000 * 60 * 60 * 24 * 15) {
            const newExpiresAt = new Date(Date.now() + 1000 * 60 * 60 * 24 * 30);
            await axios.patch(`${API_URL}/user_sessions?id=eq.${sessionId}`, {
                expires_at: newExpiresAt
            });
        }

        return session.user_id;
    } catch (error) {
        console.error("❌ Error validating session:", error);
        return null;
    }
}

export function setSessionTokenCookie(token: string, expiresAt: Date): void {
    const cookieString = `session_token=${token}; HttpOnly; SameSite=Lax; Expires=${expiresAt.toUTCString()}; Path=/`;

    if (process.env.NODE_ENV === 'production') {
        // For production (HTTPS)
        document.cookie = cookieString + "; Secure";
    } else {
        // For development (localhost)
        document.cookie = cookieString;
    console.log("Session cookie set:", document.cookie)
    }
}

export function deleteSessionTokenCookie(): void {
    document.cookie = "session_token=; HttpOnly; SameSite=Lax; Max-Age=0; Path=/";
}
