/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.text.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.Layout;
import android.text.MeasuredText;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.style.LocaleSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MeasuredTextTest {

    private static final CharSequence NULL_CHAR_SEQUENCE = null;
    private static final String STRING = "Hello, World!";
    private static final String MULTIPARA_STRING = "Hello,\nWorld!";

    private static final int SPAN_START = 3;
    private static final int SPAN_END = 7;
    private static final LocaleSpan SPAN = new LocaleSpan(Locale.US);
    private static final Spanned SPANNED;
    static {
        final SpannableStringBuilder ssb = new SpannableStringBuilder(STRING);
        ssb.setSpan(SPAN, SPAN_START, SPAN_END, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        SPANNED = ssb;
    }

    private static final TextPaint PAINT = new TextPaint();

    private static final TextDirectionHeuristic LTR = TextDirectionHeuristics.LTR;

    @Test
    public void testBuilder() {
        assertNotNull(new MeasuredText.Builder(STRING, PAINT)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build());
        assertNotNull(new MeasuredText.Builder(SPANNED, PAINT)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build());
    }

    @Test
    public void testBuilder_withNull() {
        try {
            new MeasuredText.Builder(NULL_CHAR_SEQUENCE, PAINT);
            fail();
        } catch (NullPointerException e) {
            // pass
        }
        try {
            new MeasuredText.Builder(STRING, null);
            fail();
        } catch (NullPointerException e) {
            // pass
        }
        try {
            new MeasuredText.Builder(STRING, PAINT).setTextDirection(null);
            fail();
        } catch (NullPointerException e) {
            // pass
        }
    }

    @Test
    public void testBuilder_setRange() {
        assertNotNull(new MeasuredText.Builder(STRING, PAINT).setRange(0, STRING.length()).build());
        assertNotNull(new MeasuredText.Builder(STRING, PAINT)
                .setRange(1, STRING.length() - 1).build());
        assertNotNull(new MeasuredText.Builder(SPANNED, PAINT)
                .setRange(0, SPANNED.length()).build());
        assertNotNull(new MeasuredText.Builder(SPANNED, PAINT)
                .setRange(1, SPANNED.length() - 1).build());
        try {
            new MeasuredText.Builder(STRING, PAINT).setRange(-1, -1);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }
        try {
            new MeasuredText.Builder(STRING, PAINT).setRange(100000, 100000);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }
        try {
            new MeasuredText.Builder(STRING, PAINT).setRange(STRING.length() - 1, 0);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    @Test
    public void testCharSequenceInteferface() {
        final CharSequence s = new MeasuredText.Builder(STRING, PAINT).build();
        assertEquals(STRING.length(), s.length());
        assertEquals('H', s.charAt(0));
        assertEquals('e', s.charAt(1));
        assertEquals('l', s.charAt(2));
        assertEquals('l', s.charAt(3));
        assertEquals('o', s.charAt(4));
        assertEquals(',', s.charAt(5));
        assertEquals("Hello, World!", s.toString());

        // Even measure the part of the text, the CharSequence interface still works for original
        // text.
        // TODO: Should this work like substring?
        final CharSequence s2 = new MeasuredText.Builder(STRING, PAINT)
                .setRange(7, STRING.length()).build();
        assertEquals(STRING.length(), s2.length());
        assertEquals('H', s2.charAt(0));
        assertEquals('e', s2.charAt(1));
        assertEquals('l', s2.charAt(2));
        assertEquals('l', s2.charAt(3));
        assertEquals('o', s2.charAt(4));
        assertEquals(',', s2.charAt(5));
        assertEquals("Hello, World!", s2.toString());

        final CharSequence s3 = s.subSequence(0, 3);
        assertEquals(3, s3.length());
        assertEquals('H', s3.charAt(0));
        assertEquals('e', s3.charAt(1));
        assertEquals('l', s3.charAt(2));

    }

    @Test
    public void testSpannedInterface_Spanned() {
        final Spanned s = new MeasuredText.Builder(SPANNED, PAINT).build();
        final LocaleSpan[] spans = s.getSpans(0, s.length(), LocaleSpan.class);
        assertNotNull(spans);
        assertEquals(1, spans.length);
        assertEquals(SPAN, spans[0]);

        assertEquals(SPAN_START, s.getSpanStart(SPAN));
        assertEquals(SPAN_END, s.getSpanEnd(SPAN));
        assertTrue((s.getSpanFlags(SPAN) & Spanned.SPAN_INCLUSIVE_EXCLUSIVE) != 0);

        assertEquals(SPAN_START, s.nextSpanTransition(0, s.length(), LocaleSpan.class));
        assertEquals(SPAN_END, s.nextSpanTransition(SPAN_START, s.length(), LocaleSpan.class));

        final Spanned s2 = new MeasuredText.Builder(SPANNED, PAINT)
                .setRange(7, SPANNED.length()).build();
        final LocaleSpan[] spans2 = s2.getSpans(0, s2.length(), LocaleSpan.class);
        assertNotNull(spans2);
        assertEquals(1, spans2.length);
        assertEquals(SPAN, spans2[0]);

        assertEquals(SPAN_START, s2.getSpanStart(SPAN));
        assertEquals(SPAN_END, s2.getSpanEnd(SPAN));
        assertTrue((s2.getSpanFlags(SPAN) & Spanned.SPAN_INCLUSIVE_EXCLUSIVE) != 0);

        assertEquals(SPAN_START, s2.nextSpanTransition(0, s2.length(), LocaleSpan.class));
        assertEquals(SPAN_END, s2.nextSpanTransition(SPAN_START, s2.length(), LocaleSpan.class));
    }

    @Test
    public void testSpannedInterface_String() {
        final Spanned s = new MeasuredText.Builder(STRING, PAINT).build();
        LocaleSpan[] spans = s.getSpans(0, s.length(), LocaleSpan.class);
        assertNotNull(spans);
        assertEquals(0, spans.length);

        assertEquals(-1, s.getSpanStart(SPAN));
        assertEquals(-1, s.getSpanEnd(SPAN));
        assertEquals(0, s.getSpanFlags(SPAN));

        assertEquals(s.length(), s.nextSpanTransition(0, s.length(), LocaleSpan.class));
    }

    @Test
    public void testGetText() {
        assertSame(STRING, new MeasuredText.Builder(STRING, PAINT).build().getText());
        assertSame(SPANNED, new MeasuredText.Builder(SPANNED, PAINT).build().getText());

        assertSame(STRING, new MeasuredText.Builder(STRING, PAINT)
                .setRange(1, 5).build().getText());
        assertSame(SPANNED, new MeasuredText.Builder(SPANNED, PAINT)
                .setRange(1, 5).build().getText());
    }

    @Test
    public void testGetStartEnd() {
        assertEquals(0, new MeasuredText.Builder(STRING, PAINT).build().getStart());
        assertEquals(STRING.length(), new MeasuredText.Builder(STRING, PAINT).build().getEnd());

        assertEquals(1, new MeasuredText.Builder(STRING, PAINT).setRange(1, 5).build().getStart());
        assertEquals(5, new MeasuredText.Builder(STRING, PAINT).setRange(1, 5).build().getEnd());

        assertEquals(0, new MeasuredText.Builder(SPANNED, PAINT).build().getStart());
        assertEquals(SPANNED.length(), new MeasuredText.Builder(SPANNED, PAINT).build().getEnd());

        assertEquals(1, new MeasuredText.Builder(SPANNED, PAINT).setRange(1, 5).build().getStart());
        assertEquals(5, new MeasuredText.Builder(SPANNED, PAINT).setRange(1, 5).build().getEnd());
    }

    @Test
    public void testGetTextDir() {
        assertSame(TextDirectionHeuristics.FIRSTSTRONG_LTR,
                new MeasuredText.Builder(STRING, PAINT).build().getTextDir());
        assertSame(TextDirectionHeuristics.LTR,
                new MeasuredText.Builder(SPANNED, PAINT)
                        .setTextDirection(TextDirectionHeuristics.LTR).build().getTextDir());
    }

    @Test
    public void testGetPaint() {
        // No Paint equality functions. Check only not null.
        assertNotNull(new MeasuredText.Builder(STRING, PAINT).build().getPaint());
        assertNotNull(new MeasuredText.Builder(SPANNED, PAINT).build().getPaint());
    }

    @Test
    public void testGetBreakStrategy() {
        assertEquals(Layout.BREAK_STRATEGY_HIGH_QUALITY,
                new MeasuredText.Builder(STRING, PAINT).build().getBreakStrategy());
        assertEquals(Layout.BREAK_STRATEGY_SIMPLE,
                new MeasuredText.Builder(STRING, PAINT)
                        .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build().getBreakStrategy());
    }

    @Test
    public void testGetHyphenationFrequency() {
        assertEquals(Layout.HYPHENATION_FREQUENCY_NORMAL,
                new MeasuredText.Builder(STRING, PAINT).build().getHyphenationFrequency());
        assertEquals(Layout.HYPHENATION_FREQUENCY_NONE,
                new MeasuredText.Builder(STRING, PAINT)
                        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build()
                                .getHyphenationFrequency());
    }

    @Test
    public void testGetParagraphCount() {
        final MeasuredText pm = new MeasuredText.Builder(STRING, PAINT).build();
        assertEquals(1, pm.getParagraphCount());
        assertEquals(0, pm.getParagraphStart(0));
        assertEquals(STRING.length(), pm.getParagraphEnd(0));

        final MeasuredText pm2 = new MeasuredText.Builder(STRING, PAINT).setRange(1, 9).build();
        assertEquals(1, pm2.getParagraphCount());
        assertEquals(1, pm2.getParagraphStart(0));
        assertEquals(9, pm2.getParagraphEnd(0));

        final MeasuredText pm3 = new MeasuredText.Builder(MULTIPARA_STRING, PAINT).build();
        assertEquals(2, pm3.getParagraphCount());
        assertEquals(0, pm3.getParagraphStart(0));
        assertEquals(7, pm3.getParagraphEnd(0));
        assertEquals(7, pm3.getParagraphStart(1));
        assertEquals(pm3.length(), pm3.getParagraphEnd(1));

        final MeasuredText pm4 = new MeasuredText.Builder(MULTIPARA_STRING, PAINT)
                .setRange(1, 5).build();
        assertEquals(1, pm4.getParagraphCount());
        assertEquals(1, pm4.getParagraphStart(0));
        assertEquals(5, pm4.getParagraphEnd(0));

        final MeasuredText pm5 = new MeasuredText.Builder(MULTIPARA_STRING, PAINT)
                .setRange(1, 9).build();
        assertEquals(2, pm5.getParagraphCount());
        assertEquals(1, pm5.getParagraphStart(0));
        assertEquals(7, pm5.getParagraphEnd(0));
        assertEquals(7, pm5.getParagraphStart(1));
        assertEquals(9, pm5.getParagraphEnd(1));
    }

}
