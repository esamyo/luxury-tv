/*
 * Luxury TV
 * Copyright (c) 2026 esamyo (Esam). MIT License, see LICENSE and NOTICE.md.
 * This notice must stay in every copy and modified version.
 */
package com.esamyo.luxurytv;

import java.util.Locale;

/**
 * The greeting for the hour, the way people say it where the TV's language is spoken. The hour is the TV's local
 * time, so each country gets its own morning and evening. Only greetings people use on meeting someone: "Good night"
 * is a goodbye in most languages, so the evening greeting carries on into the night there.
 */
final class Greeting {
    private Greeting() {
    }

    /** One of the good_* strings for this language (and country) at this hour, 0 to 23. */
    static int res(Locale locale, int hour) {
        String lang = locale.getLanguage();
        String country = locale.getCountry();
        switch (lang) {
            case "ar":
            case "fa":
            case "ur":
                // صباح الخير from dawn to midday, مساء الخير for the rest of the day and the night.
                return hour >= 4 && hour < 12 ? R.string.good_morning : R.string.good_evening;
            case "fr":
                // Bonjour all day, Bonsoir from early evening.
                return hour >= 5 && hour < 18 ? R.string.good_morning : R.string.good_evening;
            case "it":
                return hour >= 5 && hour < 16 ? R.string.good_morning : R.string.good_evening;
            case "de":
            case "tr":
                if (hour >= 5 && hour < 11) return R.string.good_morning;
                if (hour >= 11 && hour < 18) return R.string.good_afternoon;
                return R.string.good_evening;
            case "es":
                if ("ES".equals(country)) {
                    // Spain: "Buenas tardes" well into the evening, "Buenas noches" from 21.
                    if (hour >= 6 && hour < 12) return R.string.good_morning;
                    if (hour >= 12 && hour < 21) return R.string.good_afternoon;
                    return R.string.good_night;
                }
                // fall through: Latin America
            case "pt":
                if (hour >= 5 && hour < 12) return R.string.good_morning;
                if (hour >= 12 && hour < 19) return R.string.good_afternoon;
                return R.string.good_evening;
            case "ca":
                if (hour >= 6 && hour < 12) return R.string.good_morning;
                if (hour >= 12 && hour < 21) return R.string.good_afternoon;
                return R.string.good_night;
            case "in":
            case "id":
                // Pagi, Siang, Sore, Malam.
                if (hour < 10) return R.string.good_morning;
                if (hour < 15) return R.string.good_afternoon;
                if (hour < 18) return R.string.good_evening;
                return R.string.good_night;
            case "da":
                if (hour >= 5 && hour < 11) return R.string.good_morning;
                if (hour >= 11 && hour < 12) return R.string.good_forenoon;
                if (hour >= 12 && hour < 18) return R.string.good_afternoon;
                return R.string.good_evening;
            case "ru":
            case "uk":
                if (hour >= 4 && hour < 12) return R.string.good_morning;
                if (hour >= 12 && hour < 17) return R.string.good_afternoon;
                if (hour >= 17 && hour < 23) return R.string.good_evening;
                return R.string.good_night;
            case "nl":
                if (hour >= 6 && hour < 12) return R.string.good_morning;
                if (hour >= 12 && hour < 18) return R.string.good_afternoon;
                if (hour >= 18) return R.string.good_evening;
                return R.string.good_night;
            default:
                // English and the rest: morning, afternoon, and evening until dawn.
                if (hour >= 5 && hour < 12) return R.string.good_morning;
                if (hour >= 12 && hour < 17) return R.string.good_afternoon;
                return R.string.good_evening;
        }
    }
}
