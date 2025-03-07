import axios from "axios";
import bcrypt from "bcryptjs";
import { encodeHexLowerCase } from "@oslojs/encoding";
import { sha256 } from "@oslojs/crypto/sha2";
import { UserAuthResponse } from "../types";
import { API_URL } from "./config";
import { generateSessionToken } from "./sessions";

/**
 * Log in a user and create a session.
 */
export async function loginUser(email: string, password: string): Promise<UserAuthResponse> {
    try {
        // Fetch stored password hash from PostgREST
        const { data } = await axios.get(
            `${API_URL}/users?email=eq.${email}&select=id,password_hash`
        );

        if (data.length === 0) return { success: false, message: "Invalid credentials" };

        const user = data[0];

        // Verify password
        const isMatch = await bcrypt.compare(password, user.password_hash);
        if (!isMatch) return { success: false, message: "Invalid credentials" };

        // Generate session token
        const token = generateSessionToken();
        const sessionId = encodeHexLowerCase(sha256(new TextEncoder().encode(token)));

        // Store session in PostgREST
        await axios.post(`${API_URL}/user_sessions`, {
            id: sessionId,
            user_id: user.id,
            expires_at: new Date(Date.now() + 1000 * 60 * 60 * 24 * 30) // 30 days
        });

        // Set cookie with raw session token (client stores only this)
        const expiresAt = new Date(Date.now() + 1000 * 60 * 60 * 24 * 30); // 30 days from now
        document.cookie = `session_token=${token}; HttpOnly; SameSite=Lax; Path=/; Expires=${expiresAt.toUTCString()}`;

        return { success: true, message: "Login successful!" };
    } catch (err: any) {
        return { success: false, message: err.response?.data?.message || "Something went wrong." };
    }
}

/**
 * Log out the user by invalidating their session.
 */
export async function logoutUser() {
    const cookies = document.cookie.split("; ").reduce((acc, cookie) => {
        const [name, value] = cookie.split("=");
        acc[name] = value;
        return acc;
    }, {} as Record<string, string>);

    if (!cookies.session_token) return;

    // Hash the session token before deleting it
    const sessionId = encodeHexLowerCase(sha256(new TextEncoder().encode(cookies.session_token)));

    // Delete session from PostgREST
    await axios.delete(`${API_URL}/user_sessions?id=eq.${sessionId}`);

    // Clear the cookie
    document.cookie = `session_token=; Max-Age=0; path=/`;
}


/**
 * Sign up a new user (without creating a session).
 */
export async function signupUser(email: string, password: string, name: string): Promise<UserAuthResponse> {
    try {
        if (!name) return { success: false, message: "Name is required" };

        const passwordHash = await bcrypt.hash(password, 10);

        // Send request to PostgREST
        const response = await axios.post(`${API_URL}/users`, {
            name,
            email,
            password_hash: passwordHash
        });

        // If the response status is 201, assume success
        if (response.status === 201 || response.status === 200) {
            return { success: true, message: "Signup successful!" };
        }

        // Handle unexpected status
        console.error("❌ Unexpected response status:", response.status, response.data);
        return { success: false, message: "Failed to create user" };
    } catch (err: any) {
        // Handle errors returned from PostgREST
        console.error("Error during signup:", err);
        return { success: false, message: err.response?.data?.message || "Something went wrong." };
    }
}
