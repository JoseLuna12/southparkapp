#!/usr/bin/env python3
"""Offline checks of our chosen TV palette/target contract, not TV certification."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

values = Path(__file__).resolve().parents[1] / "app/src/main/res/values"
colors = {n.attrib["name"]: n.text for n in ET.parse(values / "colors.xml").getroot()}
dimensions = {n.attrib["name"]: n.text for n in ET.parse(values / "dimens.xml").getroot()}


def luminance(color):
    channels = [int(color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return sum(c * weight for c, weight in zip(linear, (0.2126, 0.7152, 0.0722)))


def contrast(foreground, background):
    high, low = sorted((luminance(colors[foreground]), luminance(colors[background])), reverse=True)
    return (high + 0.05) / (low + 0.05)


class TvTokenChecks(unittest.TestCase):
    def test_essential_text_contrast(self):
        # Conservative app requirement: 4.5 even for text large enough to allow 3.
        for surface in ("tv_background", "tv_surface", "tv_surface_raised", "tv_surface_selected"):
            for text in ("tv_text_primary", "tv_text_secondary", "tv_text_tertiary"):
                with self.subTest(text=text, surface=surface):
                    self.assertGreaterEqual(contrast(text, surface), 4.5)
        self.assertGreaterEqual(contrast("tv_on_accent", "tv_accent"), 4.5)
        self.assertGreaterEqual(contrast("tv_on_accent", "tv_accent_pressed"), 4.5)
        self.assertGreaterEqual(contrast("tv_error", "tv_background"), 4.5)
        self.assertGreaterEqual(contrast("tv_disabled", "tv_disabled_surface"), 4.5)

    def test_focus_contrast(self):
        for surface in ("tv_background", "tv_surface", "tv_surface_selected"):
            with self.subTest(surface=surface):
                self.assertGreaterEqual(contrast("tv_accent", surface), 3)

    def test_remote_target_contract(self):
        for name in ("tv_button_min_height", "tv_rail_row_height"):
            with self.subTest(token=name):
                self.assertTrue(dimensions[name].endswith("dp"))
                self.assertGreaterEqual(float(dimensions[name][:-2]), 48)


if __name__ == "__main__":
    unittest.main()
