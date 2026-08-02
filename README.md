# Satchmo Template

SatchmoGDX template is a simple, community-coordinated, and Liftoff-supported shortcut to quickly fork `gdx-core` and -
if desired - share the fork with the wider community.

[blah, blah, blah, just tell me how to use it](#getting-started)

## Rationale

LibGDX is Open Source.

In Open Source, changing an upstream library should be easy, whether the change is shared with the community or private;
reusable or a one-time hack. This ease ensures healthy growth of the library and drives the feeling of shared
ownership and responsibility among users.

But as of summer 2026, LibGDX is hard to modify and fork due to two major, but artificial, barriers:

- **The LibGDX monorepo is huge; the build is complex.** A developer trying to change UI handling or fix a Tiled
  rendering bug is immediately faced with the complexity of multiplatform natives, build scripts, etc.
- **Discovery and reuse are hard.** Even if a fork is created, it is difficult to reuse because it is not supported by
  the official setup tool. The only way to share a fork with the wider community would be to also maintain a Liftoff
  fork, raising further technical and discoverability issues.

The gap between the people who know what abstractions they need (game developers, LibGDX users) and those who know how
to deal with the complex environement (LibGDX maintainers) ultimately hurts the community by giving a raise to an "
us/them" mentality.

This author believes that tearing those two barriers down could unlock an immense resource: the talent, experience and
knowledge of those LibGDX community members who have so far only thought of themselves as customers of the library.

## The Satchmo Philosophy

A lot of people associate forking with fracturing communities: splintering, dividing, secession. This is the opposite of
what Satchmo is about! We care deeply about the community (and have been a part of it since the beginning of LibGDX),
and the entire point is to make LibGDX development more inclusive and the LibGDX users more involved.

We think of Satchmo as friendly, forward-looking, quality-of-life-focused, naive and parasitic:

* **friendly** - because instead of splitting LibGDX and bifurcating the community, we encourage everyone to scout ahead
  and explore the immense solution space of game frameworks. Gamedevs should be able to innovate without assuming the
  heavy baggage of stability and maturity of **LibGDX**.
* **forward-looking** - Satchmo forks are meant to incubate and inspire features that can be adopted upstream, lessening
  the load on the **LibGDX** core maintainers. We hope to grow into the LibGDX of tomorrow.
* **quality-of-life-focused** - Satchmo forks are not meant for creating new backends, challenging core architecture
  decisions, or rewriting LibGDX in Rust. We want TikTok-worthy features that excite game developers and ensure the
  project's immortality through **generational succession** (*przemiana pokoleń*).
* **naive** - The idea behind the Satchmo template is unsophisticated. We work around the complexity
  of making a "real" fork by literally copying the source JAR into the template repo and writing over it.
* **parasitic** - SatchmoGDX depends not only on LibGDX sources, but also on its infrastructure (i.e., the fact that the
  JARs are built and published along with platform natives). We are doing exactly
  [what Duchamp did](https://www.moma.org/audio/playlist/352/4918).

## Getting Started

Using the Satchmo template to create your own fork is a three-step process:

### 1. Fork the Template

Create your own repository based on satchmo-template, the simplest way is allegedly clicking the **"Use this template"**
button on GitHub (or so I have been told by an LLM).

### 2. Configure your Fork

Edit `gradle.properties` in your new repository:

- `forkName`: The name of your project (e.g., `gdx-pro`).
- `group`: Your Maven group ID (e.g., `com.yourname.gdx`).
- `version`: Your version (e.g., `1.0.0-SNAPSHOT`).

### 3. Evolve

The `:gdx` submodule is a copy of `gdx-core`, this is where you can add features, fix bugs and improve the codebase.

All these sources are yours — except `/gdx/src/glue`. Attempt no modifications there.

This constraint is in place to ensure that your fork remains compatible with the original LibGDX backends. With care,
you can probably remove it.

### 4. Use and Share

To use your fork in a LibGDX project, publish it (local maven repository is enough for private use - run
`publishToMavenLocal` task).

Then create a LibGDX project as usual (likely with Liftoff) and configure a substitution in the top-level `build.gradle`
file. This is done by adding the following code to the `allprojects` block, e.g.:

```groovy
    configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute module("com.badlogicgames.gdx:gdx") using module("com.yourname.gdx:gdx:1.0.0-SNAPSHOT")
    }
}
```

Note that, for technical reasons (don't ask, GWT/Gradle), the artifact name must be always called `gdx`. Each
satchmo-based fork must have a unique group coordinate.

## License and Authorship

Satchmo-template is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

All licensing data and copyright notices of the original LibGDX project are preserved
and [available in the licensing/ directory](licensing/).
