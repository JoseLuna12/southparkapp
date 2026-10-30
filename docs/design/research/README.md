# Design research archive

These are read-only investigation reports used for the v0.5 native TV redesign. They distinguish Android TV guidance, general Android guidance and app-selected values, with source links and uncertainty notes.

- [Foundations](foundations.md): color, type, layout, imagery, shape, focus and motion.
- [Navigation](navigation.md): remote navigation, catalog and focus recovery.
- [Playback](playback.md): Media3, provider boundaries, controls and sequential playback.
- [Accessibility](accessibility.md): remote/TalkBack, contrast, scaling and localization.
- [Reconciled proposal](reconciled-contract.md): research synthesis, **not the final implemented specification**.
- [Pre-redesign code audit](codebase-audit.md): architecture and preservation risks before implementation.

## Precedence

The implemented contract is [docs/design-system.md](../../design-system.md), backed by resources/source and [VALIDATION.md](../../../VALIDATION.md). Research proposals do not authorize new features or override the product's existing behavior.

Not adopted: shelf migration, Compose/RecyclerView migration, a pre-roll autoplay countdown, custom transport controls, focus enlargement/animation, or platform Watch Next integration. We retained the season rail/three-column grid and immediate content-end autoplay. Focus uses static high-contrast geometry rather than scale, avoiding clipping and motion dependence. Concrete radii, sizes and colors in code supersede suggested starting values in these reports.

The safe-area sources contain conflicting exact numbers. Our 48dp/27dp starting insets are an app choice on the 960×540dp reference canvas, not a universal Android TV requirement. Official TV guidance takes precedence over generic phone examples. Screenshots/emulator tests are not physical-TV or accessibility certification.
