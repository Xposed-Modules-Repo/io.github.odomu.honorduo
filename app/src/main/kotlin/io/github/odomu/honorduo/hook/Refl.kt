/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 * Copyright (C) 2026 odomu and HonorDuo contributors
 *
 * This file is part of HonorDuo, a fork of HyperDuo, released under the GNU
 * General Public License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.hook

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Small reflection helper. Nothing here throws: a missing class, field or method
 * degrades to `null`, and a failed read to the caller's default.
 *
 * The host is a different APK whose internals this module must read from a
 * class loader it does not own, so every access is best-effort by design.
 */
internal object Refl {

    @JvmStatic
    fun cls(name: String, cl: ClassLoader): Class<*>? = try {
        Class.forName(name, false, cl)
    } catch (_: Throwable) {
        null
    }

    /** The field declared on [c] or any superclass, made accessible. */
    @JvmStatic
    fun field(c: Class<*>?, name: String): Field? {
        var k: Class<*>? = c
        while (k != null) {
            try {
                return k.getDeclaredField(name).also { it.isAccessible = true }
            } catch (_: Throwable) {
                k = k.superclass
            }
        }
        return null
    }

    @JvmStatic
    fun method(c: Class<*>?, name: String, vararg sig: Class<*>): Method? {
        if (c == null) return null
        return try {
            c.getDeclaredMethod(name, *sig).also { it.isAccessible = true }
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun methodDeep(c: Class<*>?, name: String, vararg sig: Class<*>): Method? {
        var k: Class<*>? = c
        while (k != null && k != Any::class.java) {
            try {
                return k.getDeclaredMethod(name, *sig).also { it.isAccessible = true }
            } catch (_: Throwable) {
                k = k.superclass
            }
        }
        return null
    }

    /** The declared method with this name and arity, whatever its signature. */
    @JvmStatic
    fun get(f: Field?, o: Any?): Any? {
        if (f == null || o == null) return null
        return try {
            f.get(o)
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun getInt(f: Field?, o: Any?, def: Int): Int = (get(f, o) as? Number)?.toInt() ?: def
    @JvmStatic
    fun getBool(f: Field?, o: Any?, def: Boolean): Boolean = (get(f, o) as? Boolean) ?: def

    @JvmStatic
    fun set(f: Field?, o: Any?, v: Any?) {
        if (f == null || o == null) return
        try {
            f.set(o, v)
        } catch (_: Throwable) {
            // best effort
        }
    }

    @JvmStatic
    fun invoke(m: Method?, o: Any?): Any? {
        if (m == null || o == null) return null
        return try {
            m.invoke(o)
        } catch (_: Throwable) {
            null
        }
    }
}
