/*
 * Copyright 2023 The Android Open Source Project
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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.transformer.TestUtil.ASSET_URI_PREFIX;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_VIDEO;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S;
import static com.google.android.exoplayer2.transformer.TestUtil.createEncodersAndDecoders;
import static com.google.android.exoplayer2.transformer.TestUtil.createTransformerBuilder;
import static com.google.android.exoplayer2.transformer.TestUtil.getDumpFileName;
import static com.google.android.exoplayer2.transformer.TestUtil.removeEncodersAndDecoders;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.audio.SonicAudioProcessor;
import com.google.android.exoplayer2.effect.RgbFilter;
import com.google.android.exoplayer2.testutil.DumpFileAsserts;
import com.google.android.exoplayer2.transformer.TestUtil.TestMuxerFactory.TestMuxerHolder;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * End-to-end test for exporting a single {@link EditedMediaItemSequence} containing multiple {@link
 * EditedMediaItem} instances with {@link Transformer}.
 */
@RunWith(AndroidJUnit4.class)
public final class SequenceExportTest {

  private Context context;
  private String outputPath;
  private TestMuxerHolder testMuxerHolder;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    outputPath = Util.createTempFile(context, "TransformerTest").getPath();
    testMuxerHolder = new TestMuxerHolder();
    createEncodersAndDecoders();
  }

  @After
  public void tearDown() throws Exception {
    Files.delete(Paths.get(outputPath));
    removeEncodersAndDecoders();
  }

  @Test
  public void start_concatenateSameMediaItemWithTransmux_completesSuccessfully() throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem, editedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(editedMediaItemSequence))
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_VIDEO + ".concatenated_transmux"));
  }

  @Test
  public void start_concatenateSameMediaItemWithEffectsAndTransmux_ignoresEffects()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setPitch(2f);
    Effect videoEffect = RgbFilter.createGrayscaleFilter();
    Effects effects =
        new Effects(ImmutableList.of(sonicAudioProcessor), ImmutableList.of(videoEffect));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();
    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem, editedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(editedMediaItemSequence))
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_VIDEO + ".concatenated_transmux"));
  }

  @Test
  public void start_concatenateClippedMediaItemsWithTransmux_completesSuccessfully()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem.ClippingConfiguration clippingConfiguration1 =
        new MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(0) // Corresponds to key frame.
            .setEndPositionMs(500)
            .build();
    MediaItem mediaItem1 =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S)
            .setClippingConfiguration(clippingConfiguration1)
            .build();
    EditedMediaItem editedMediaItem1 = new EditedMediaItem.Builder(mediaItem1).build();
    MediaItem.ClippingConfiguration clippingConfiguration2 =
        new MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(12_500) // Corresponds to key frame.
            .setEndPositionMs(14_000)
            .build();
    MediaItem mediaItem2 =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S)
            .setClippingConfiguration(clippingConfiguration2)
            .build();
    EditedMediaItem editedMediaItem2 = new EditedMediaItem.Builder(mediaItem2).build();
    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem1, editedMediaItem2));
    Composition composition =
        new Composition.Builder(ImmutableList.of(editedMediaItemSequence))
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(
            FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S + ".clipped_concatenated_transmux"));
  }

  @Test
  public void start_concatenateSilenceAndAudioWithTransmuxVideo_completesSuccessfully()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    EditedMediaItem noAudioEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();
    EditedMediaItem audioEditedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence(ImmutableList.of(noAudioEditedMediaItem, audioEditedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(sequence))
            .experimentalSetForceAudioTrack(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_VIDEO + ".silence_then_audio"));
  }

  @Test
  public void start_concatenateSilenceAndAudioWithEffectsAndTransmuxVideo_completesSuccessfully()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setPitch(2f);
    Effects effects =
        new Effects(ImmutableList.of(sonicAudioProcessor), /* videoEffects= */ ImmutableList.of());
    EditedMediaItem noAudioEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).setEffects(effects).build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence(ImmutableList.of(noAudioEditedMediaItem, audioEditedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(sequence))
            .experimentalSetForceAudioTrack(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_VIDEO + ".silence_then_audio_with_effects"));
  }

  @Test
  public void start_concatenateSameAudioItemWithEffects_completesSuccessfully() throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setPitch(2f);
    Effects effects =
        new Effects(ImmutableList.of(sonicAudioProcessor), /* videoEffects= */ ImmutableList.of());

    // The video track must be removed in order for the export to end. Indeed, the
    // Robolectric decoder just copies the input buffers to the output and the audio timestamps are
    // therefore computed based on the encoded samples (see [internal: b/178685617]). As a result,
    // the audio timestamps are much smaller than they should be and the muxer waits for more audio
    // samples before writing video samples.
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).setRemoveVideo(true).build();
    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem, editedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(editedMediaItemSequence)).build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_VIDEO + ".concatenated_audio_high_pitch"));
  }
}
