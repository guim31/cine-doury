#!/usr/bin/env python3
"""Plays the 20 gestures of docs/GESTES.md on a device with Ciné Doury signed in, and times them.

Usage: couch-gestures.sh ADB_SERIAL [--package fr.guim.cinedoury] [--out FILE] [--only N,M]

Keys go through `adb shell input keyevent`, as a remote would. Each gesture ends on what the app
says in logcat (`GestureLog`, enabled here by `setprop log.tag.CineDoury DEBUG`: a key received, a
screen drawn, the picture playing) or, where nothing is logged, on what the screen shows
(`uiautomator dump`, the test tags of the app being resource ids). No blind sleep: every wait has a
condition and a timeout.

Times are taken on the device clock. « Total »: from the first key to the end, the delay of `input`
included (about 0.1 to 0.3 s per key on a TV). « Réponse »: from the last key received by the app to
the end, what a hand on the remote feels. A time marked ≈ is seen through `uiautomator dump`, to
within a second.

It knows no title of the server: « the first card of the first row », « the first film of the
grid ». A gesture that cannot be done there (no title to resume, no series, not an administrator)
is skipped and said so; the others go on. Nothing is changed on the server: what a gesture changes
(seen, subtitles) it puts back.
"""

import argparse
import re
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ElementTree

LOG_LINE = re.compile(r"^\s*(\d+\.\d+)\s+.*?CineDoury\s*:\s+gesture (.+)$")

KEY = {
    "up": "KEYCODE_DPAD_UP",
    "down": "KEYCODE_DPAD_DOWN",
    "left": "KEYCODE_DPAD_LEFT",
    "right": "KEYCODE_DPAD_RIGHT",
    "ok": "KEYCODE_DPAD_CENTER",
    "back": "KEYCODE_BACK",
}
TIMEOUT = 15.0
# A hand between two keys: the app gets the time to move the focus, as on a sofa.
PAUSE = 0.25


class Skip(Exception):
    """The gesture cannot be done on this server or this profile."""


class Failed(Exception):
    """The app did not answer as expected."""


class Device:
    def __init__(self, serial, package):
        self.serial = serial
        self.package = package
        self.events = []  # (device time in s, event)
        self.seen = set()
        self.lock = threading.Lock()
        self.presses = 0

    # adb ------------------------------------------------------------------------------------------

    def adb(self, *args, check=True, timeout=60):
        result = subprocess.run(
            ["adb", "-s", self.serial, *args], capture_output=True, text=True, timeout=timeout
        )
        if check and result.returncode != 0:
            raise Failed(f"adb {' '.join(args)}: {result.stderr.strip()}")
        return result.stdout

    def shell(self, command, **kwargs):
        return self.adb("shell", command, **kwargs)

    def now(self):
        """The device clock, in seconds: the same as the timestamps of logcat."""
        return float(self.shell("date +%s.%N").strip())

    # logcat ---------------------------------------------------------------------------------------

    def start_log(self):
        self.shell("setprop log.tag.CineDoury DEBUG")
        # A larger buffer keeps the lines of the app longer, while the player logs a lot.
        self.adb("logcat", "-G", "16M", check=False)
        self.adb("logcat", "-c", check=False)

    def stop_log(self):
        self.shell("setprop log.tag.CineDoury ''", check=False)

    def refresh(self):
        """Reads the lines of the app logged since the start (`adb logcat -d`: a dump, not a stream)."""
        out = self.adb("logcat", "-d", "-v", "epoch", "-s", "CineDoury:D", check=False, timeout=30)
        events = []
        for line in out.splitlines():
            match = LOG_LINE.match(line)
            if match:
                events.append((float(match.group(1)), match.group(2).strip()))
        with self.lock:
            # The buffer of logcat rotates (playback logs a lot): a dump may have lost the first lines
            # of the session. Keep what was read, add the lines not seen yet, in their order.
            for event in events:
                if event not in self.seen:
                    self.seen.add(event)
                    self.events.append(event)

    def diagnose(self):
        """What the device says when the app is silent: for the message of a failure."""
        lines = [
            "log.tag.CineDoury = " + self.shell("getprop log.tag.CineDoury", check=False).strip(),
            self.shell("dumpsys activity activities | grep -E 'ResumedActivity|topResumed' | head -3", check=False).strip(),
            self.adb("logcat", "-d", "-v", "epoch", "-t", "30", "-s", "CineDoury:V", "AndroidRuntime:E", check=False).strip(),
        ]
        return "\n".join(lines)

    def mark(self):
        """Where the events of what follows start."""
        self.refresh()
        with self.lock:
            return len(self.events)

    def wait_event(self, predicate, since, timeout=TIMEOUT, what=""):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            self.refresh()
            with self.lock:
                for at, event in self.events[since:]:
                    if predicate(event):
                        return at
            time.sleep(0.2)
        raise Failed(f"no « {what} » in the log after {timeout:.0f} s")

    def last_key(self, since):
        self.refresh()
        with self.lock:
            keys = [at for at, event in self.events[since:] if event.startswith("key ")]
        return keys[-1] if keys else None

    def first_key(self, since):
        with self.lock:
            keys = [at for at, event in self.events[since:] if event.startswith("key ")]
        return keys[0] if keys else None

    # keys and screen ------------------------------------------------------------------------------

    def press(self, *names, pause=PAUSE):
        for name in names:
            self.shell(f"input keyevent {KEY[name]}")
            self.presses += 1
            if pause:
                time.sleep(pause)

    def dump(self):
        """The screen as accessibility sees it; None when it is busy (an animation running)."""
        for _ in range(3):
            out = self.adb("exec-out", "uiautomator", "dump", "--compressed", "/dev/tty", check=False, timeout=30)
            start = out.find("<?xml")
            end = out.rfind("</hierarchy>")
            if start >= 0 and end > start:
                try:
                    return ElementTree.fromstring(out[start : end + len("</hierarchy>")])
                except ElementTree.ParseError:
                    pass
            time.sleep(0.3)
        return None

    def wait_ui(self, condition, timeout=TIMEOUT, what=""):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            root = self.dump()
            if root is not None:
                found = condition(Screen(root))
                if found:
                    return found
            time.sleep(0.2)
        raise Failed(f"the screen never showed « {what} » after {timeout:.0f} s")

    def screen(self):
        root = self.dump()
        if root is None:
            raise Failed("uiautomator could not read the screen")
        return Screen(root)


class Screen:
    """One `uiautomator dump`: the nodes by resource id (the test tags of the app)."""

    def __init__(self, root):
        self.root = root
        self.parents = {child: parent for parent in root.iter() for child in parent}

    def nodes(self, tag=None, prefix=None):
        for node in self.root.iter("node"):
            rid = node.get("resource-id", "")
            if (tag is not None and rid == tag) or (prefix is not None and rid.startswith(prefix)):
                yield node

    def has(self, tag=None, prefix=None):
        return any(True for _ in self.nodes(tag, prefix))

    def focused(self):
        nodes = [n for n in self.root.iter("node") if n.get("focused") == "true" and n.get("package", "")]
        # The deepest focused node: the view that holds Compose is focused too.
        return nodes[-1] if nodes else None

    def focus_tag(self, prefix):
        """The tag starting with [prefix] on the focused node or one of its parents."""
        node = self.focused()
        while node is not None:
            rid = node.get("resource-id", "")
            if rid.startswith(prefix):
                return rid
            node = self.parents.get(node)
        return None

    def focus_in(self, tag):
        return self.focus_tag(tag) == tag

    def selected(self, prefix):
        """The tags starting with [prefix] whose node, or a node inside, is selected."""
        result = []
        for node in self.nodes(prefix=prefix):
            if node.get("selected") == "true" or any(n.get("selected") == "true" for n in node.iter("node")):
                result.append(node.get("resource-id"))
        return result

    def description(self, tag):
        for node in self.nodes(tag):
            for inner in node.iter("node"):
                text = inner.get("content-desc") or inner.get("text")
                if text:
                    return text
        return None


# The gestures --------------------------------------------------------------------------------------


class Couch:
    def __init__(self, device):
        self.d = device
        self.results = []
        self.resume_row = None

    def run(self, number, name, presses, target, gesture, from_start=False):
        """Plays [gesture]; it returns the device time of its end, seen in the log, or None when
        its end was seen on the screen (to within a dump: « ≈ »)."""
        d = self.d
        d.presses = 0
        since = d.mark()
        start = d.now()
        try:
            end = gesture(since)
            approx = end is None
            if end is None:
                end = d.now()
            first = d.first_key(since)
            last = d.last_key(since)
            origin = start if from_start or first is None else first
            total = end - origin
            response = end - last if last is not None and end >= last else None
            status = "ok" if d.presses <= presses else f"ok, {d.presses} appuis"
            self.results.append((number, name, d.presses, presses, total, response, target, status, approx))
        except Skip as skip:
            self.results.append((number, name, d.presses, presses, None, None, target, f"sauté : {skip}", False))
        except (Failed, subprocess.TimeoutExpired) as failure:
            self.results.append((number, name, d.presses, presses, None, None, target, f"échec : {failure}", False))
            print(self.context(since), flush=True)
            self.recover()

    def context(self, since):
        """What the app said during a failed gesture and where its focus is: printed under the failure."""
        d = self.d
        d.refresh()
        with d.lock:
            events = [e for _, e in d.events[since:]][-16:]
        root = d.dump()
        chain = []
        if root is not None:
            s = Screen(root)
            node = s.focused()
            while node is not None:
                if node.get("resource-id"):
                    chain.append(node.get("resource-id"))
                node = s.parents.get(node)
        focus = " < ".join(chain) if root is not None else "(écran illisible : lecteur ou animation)"
        return f"      journal : {' · '.join(events) or '(rien)'}\n      focus : {focus or '(aucune étiquette)'}"

    def screen_event(self, name):
        return lambda event: event == f"screen {name}"

    def wait_screen(self, name, since, timeout=TIMEOUT):
        return self.d.wait_event(self.screen_event(name), since, timeout, f"écran {name}")

    def back_to(self, name):
        """« Retour » until the screen [name] is drawn; returns when. A keyboard left attached to the
        window (seen on the emulator, after the sign-in tests) takes some « Retour » for itself: one
        more, as a hand would; the response counts from the last."""
        d = self.d
        mark = d.mark()
        for _ in range(3):
            d.press("back", pause=0)
            try:
                return self.wait_screen(name, mark, 4)
            except Failed:
                continue
        return self.wait_screen(name, mark)

    def recover(self):
        """Back to the home, « Lire » focused, from wherever a failed gesture left the app; at worst
        the app is started again."""
        try:
            self.home()
        except (Failed, Skip, subprocess.TimeoutExpired):
            try:
                self.start(cold=True)
            except (Failed, Skip, subprocess.TimeoutExpired) as failure:
                print(f"    (l'appli ne revient pas à l'accueil : {failure})", flush=True)

    def start(self, cold):
        """Starts the app (from nothing when [cold]) to its home, through « Qui regarde ? » if it asks;
        returns when the home was drawn (the log), once « Lire » has the focus."""
        d = self.d
        if cold:
            d.shell(f"am force-stop {d.package}")
        since = d.mark()
        d.shell(f"am start -n {d.package}/{d.package}.tv.MainActivity")
        try:
            first, at = self.next_screen(since, ("Home", "Profiles", "Pin", "Unreachable"))
        except Failed as failure:
            raise Failed(f"{failure}\n{d.diagnose()}") from failure
        if first == "Profiles":
            # « Qui regarde ? » (its default, « Toujours demander »): OK on the profile focused, the last one.
            d.wait_ui(lambda s: s.focus_tag("profile-"), what="un profil focalisé")
            since = d.mark()
            d.press("ok", pause=0)
            first, at = self.next_screen(since, ("Home", "Pin", "QuickConnect", "Password"))
        if first != "Home":
            raise Skip(f"l'appli ne s'ouvre pas sur l'accueil ({first}) : connectez un profil sans code")
        self.wait_home_focus()
        return at

    def next_screen(self, since, names):
        """The first of the screens [names] drawn since [since], and when."""
        d = self.d
        wanted = {f"screen {n}" for n in names}
        at = d.wait_event(lambda e: e in wanted, since, 30, "l'écran suivant")
        with d.lock:
            event = next(e for t, e in d.events[since:] if t == at and e in wanted)
        return event.removeprefix("screen "), at

    def wait_home_focus(self):
        """« Lire » focused: the home has its rows (from the cache, then the server)."""
        self.d.wait_ui(lambda s: s.focus_in("home-play"), what="« Lire » focalisé")

    def home(self):
        """From anywhere in the app: « Retour » until the home, then « Lire »."""
        d = self.d
        for _ in range(12):
            root = d.dump()
            if root is None:
                # The player, never still for uiautomator: « Retour » leaves it. Elsewhere, an
                # animation: wait for it rather than leave the screen.
                d.refresh()
                if self.last_screen() == "Player":
                    d.press("back")
                else:
                    time.sleep(1)
                continue
            s = Screen(root)
            if s.has("quit-dialog"):
                d.press("back")
            elif s.has("home"):
                if s.focus_in("home-play"):
                    return
                d.press("right" if s.focus_tag("rail") else "back")
            else:
                d.press("back")
        raise Failed("impossible de revenir à l'accueil")

    def to_rail_entry(self, tag):
        d = self.d
        d.press("left")
        d.wait_ui(lambda s: s.focus_tag("rail-"), what="le rail")
        for direction in ("up", "down"):
            for _ in range(14):
                s = d.screen()
                if s.focus_in(tag):
                    return
                if not s.has(tag):
                    raise Skip(f"pas d'entrée {tag} dans le rail")
                before = s.focus_tag("rail-")
                d.press(direction)
                after = d.screen().focus_tag("rail-")
                if after == before:
                    break
        raise Failed(f"{tag} jamais atteint dans le rail")

    def to_button(self, tag, direction="right", limit=8):
        d = self.d
        for _ in range(limit):
            s = d.screen()
            if s.focus_in(tag):
                return
            if not s.has(tag):
                raise Skip(f"pas de bouton {tag} ici")
            d.press(direction)
        raise Failed(f"{tag} jamais focalisé")

    # 1 to 20 --------------------------------------------------------------------------------------

    def g01_cold_start(self, since):
        return self.start(cold=True)

    def g02_resume_details(self, since):
        d = self.d
        self.home()
        s = d.screen()
        rows = [n.get("resource-id") for n in s.nodes(prefix="row-")]
        resume = next((r for r in rows if "resume" in r.lower()), None)
        if resume is None:
            raise Skip("pas de rangée « Reprendre » à l'accueil")
        key = resume.removeprefix("row-")
        since = d.mark()
        for _ in range(8):
            d.press("down")
            if d.screen().focus_tag(f"card-{key}-"):
                break
        else:
            raise Failed("la rangée « Reprendre » n'a jamais le focus")
        since_ok = d.mark()
        d.press("ok", pause=0)
        return self.wait_screen("Details", since_ok)

    def g03_play_from_details(self, since):
        d = self.d
        if not d.screen().has("details"):
            raise Skip("pas sur une fiche (geste 2 sauté)")
        d.wait_ui(lambda s: s.focus_in("details-play"), what="« Reprendre » focalisé")
        since = d.mark()
        d.press("ok", pause=0)
        return d.wait_event(lambda e: e == "playing", since, 30, "lecture")

    def g04_show_osd(self, since):
        d = self.d
        self.in_player()
        since = d.mark()
        d.press("ok", pause=0)
        return d.wait_event(lambda e: e == "osd shown", since, what="interface du lecteur")

    def g05_pause_resume(self, since):
        d = self.d
        self.in_player(osd=True)
        mark = d.mark()
        d.press("ok")
        d.wait_event(lambda e: e == "paused", mark, what="pause")
        mark = d.mark()
        d.press("ok", pause=0)
        return d.wait_event(lambda e: e == "playing", mark, what="reprise")

    def g06_jump(self, since):
        d = self.d
        self.in_player()
        mark = d.mark()
        d.press("right", pause=0)
        # The jump seeks: the picture plays again at its new place.
        return d.wait_event(lambda e: e == "jumped", mark, 20, "lecture après le saut")

    def g07_subtitles(self, since):
        d = self.d
        self.in_player(osd=True)
        at = self.focus_player("down", lambda tag: tag != "player-progress")
        self.to_control(at, "player-tracks")
        mark = d.mark()
        d.press("ok")
        d.wait_event(lambda e: e == "panel Tracks", mark, what="panneau Audio et sous-titres")
        d.wait_event(lambda e: e.startswith("column 1 "), mark, what="colonne des sous-titres")
        lines, checked = self.column(mark, 1)
        # The lines: « Aucun », the subtitles of the file, then « Taille, position et fond ».
        choices = list(range(lines - 1))
        if len(choices) < 2:
            self.close_panel()
            raise Skip("aucun sous-titre dans ce titre")
        at = self.focus_player("right", lambda tag: tag.startswith("player-panel-1-"))
        current = int(at.rsplit("-", 1)[1])
        target = min((c for c in choices if c != checked), key=lambda c: abs(c - current))
        self.move_to(current, target)
        mark = d.mark()
        d.press("ok", pause=0)
        end = d.wait_event(lambda e: e.startswith("subtitles"), mark, what="sous-titres changés")
        # Put the subtitles back as they were.
        self.move_to(target, checked)
        mark = d.mark()
        d.press("ok")
        d.wait_event(lambda e: e.startswith("subtitles"), mark, what="sous-titres remis")
        self.close_panel()
        return end

    def to_control(self, at, target):
        """Along the row of buttons of the player (← or →, by their order) until [target]."""
        for _ in range(len(CONTROLS)):
            if at == target:
                return
            if at not in CONTROLS:
                raise Failed(f"le focus du lecteur est sur {at}")
            key = "left" if CONTROLS.index(target) < CONTROLS.index(at) else "right"
            at = self.focus_player(key, lambda tag: True)
        raise Failed(f"{target} jamais focalisé")

    def column(self, since, number):
        with self.d.lock:
            line = next(e for _, e in self.d.events[since:] if e.startswith(f"column {number} "))
        values = dict(part.split("=") for part in line.split()[2:])
        return int(values["lines"]), int(values["checked"])

    def move_to(self, current, target):
        direction = "down" if target > current else "up"
        for _ in range(abs(target - current)):
            self.focus_player(direction, lambda tag: True)

    def focus_player(self, key, accept, limit=1):
        """Presses [key] until the player says the focus is on a tag [accept] takes."""
        d = self.d
        for _ in range(limit):
            self.show_osd()
            mark = d.mark()
            d.press(key, pause=0)
            try:
                d.wait_event(lambda e: e.startswith("focus "), mark, 5, "le focus")
            except Failed:
                # The interface hid itself (4 s without a key) as ↓ left: that key only showed it,
                # and the focus is on the bar again. Any other key would mean something else there
                # (→ jumps): no second try.
                if self.osd_visible() or key != "down":
                    raise
                self.show_osd()
                mark = d.mark()
                d.press(key, pause=0)
                d.wait_event(lambda e: e.startswith("focus "), mark, 5, "le focus")
            tag = self.last_focus()
            if accept(tag):
                return tag
        raise Failed(f"le focus du lecteur est sur {self.last_focus()}")

    def osd_visible(self):
        self.d.refresh()
        with self.d.lock:
            shown = [e for _, e in self.d.events if e.startswith("osd ")]
        return bool(shown) and shown[-1] == "osd shown"

    def show_osd(self):
        """The interface of the player shown (OK when hidden), unless a panel is open."""
        d = self.d
        with d.lock:
            panels = [e for _, e in d.events if e.startswith("panel ")]
        if (panels and panels[-1] != "panel closed") or self.osd_visible():
            return
        mark = d.mark()
        d.press("ok")
        d.wait_event(lambda e: e == "osd shown", mark, what="interface du lecteur")

    def last_focus(self):
        with self.d.lock:
            focus = [e for _, e in self.d.events if e.startswith("focus ")]
        return focus[-1].removeprefix("focus ") if focus else None

    def close_panel(self):
        """« Retour » until the panel of the player closes. A keyboard left attached to the window
        (seen on the emulator, after the sign-in tests) takes a first « Retour » for itself."""
        d = self.d
        mark = d.mark()
        for _ in range(3):
            d.press("back")
            try:
                d.wait_event(lambda e: e == "panel closed", mark, 4, "panneau fermé")
                return
            except Failed:
                continue
        d.wait_event(lambda e: e == "panel closed", mark, what="panneau fermé")

    def g08_leave_player(self, since):
        d = self.d
        self.in_player()
        return self.leave_player()

    def g09_details_to_home(self, since):
        d = self.d
        s = d.screen()
        if not s.has("details"):
            raise Skip("pas sur une fiche (un geste précédent n'y a pas mené)")
        return self.back_to("Home")

    def g10_play_featured(self, since):
        d = self.d
        self.home()
        mark = d.mark()
        d.press("ok", pause=0)
        end = d.wait_event(lambda e: e == "playing", mark, 30, "lecture")
        self.leave_player()
        return end

    def g11_open_library(self, since):
        d = self.d
        self.home()
        d.press("left")
        d.wait_ui(lambda s: s.focus_tag("rail-"), what="le rail")
        for _ in range(4):
            if d.screen().focus_tag("rail-library-"):
                break
            d.press("down")
        else:
            raise Skip("aucune bibliothèque dans le rail")
        mark = d.mark()
        d.press("ok", pause=0)
        return self.wait_screen("Library", mark)

    def last_screen(self):
        with self.d.lock:
            screens = [e for _, e in self.d.events if e.startswith("screen ")]
        return screens[-1].removeprefix("screen ") if screens else None

    def g12_scroll_grid(self, since):
        d = self.d
        if self.last_screen() != "Library":
            raise Skip("pas dans une bibliothèque (un geste précédent n'y a pas mené)")
        try:
            first = d.wait_ui(lambda s: s.focus_tag("item-"), timeout=5, what="une carte de la grille")
        except Failed:
            # The focus stayed on the rail: → enters the grid.
            d.press("right")
            first = d.wait_ui(lambda s: s.focus_tag("item-"), what="une carte de la grille")
        start = int(first.removeprefix("item-"))
        d.press(*["down"] * 5)
        # Accessibility tells the new focus late: until it differs from the first card.
        try:
            moved = d.wait_ui(
                lambda s: (tag := s.focus_tag("item-")) is not None and tag != first and tag,
                timeout=10,
                what="une autre carte de la grille",
            )
        except Failed:
            raise Skip("la bibliothèque n'a pas cinq rangées") from None
        if int(moved.removeprefix("item-")) <= start:
            raise Skip("la bibliothèque n'a pas cinq rangées")
        return None

    def g13_open_from_grid(self, since):
        d = self.d
        self.grid_card = d.screen().focus_tag("item-")
        if self.grid_card is None:
            raise Skip("pas dans une grille (un geste précédent n'y a pas mené)")
        mark = d.mark()
        d.press("ok", pause=0)
        return self.wait_screen("Details", mark)

    def g14_watched(self, since):
        d = self.d
        if self.last_screen() != "Details":
            raise Skip("pas sur une fiche (un geste précédent n'y a pas mené)")
        d.wait_ui(lambda s: s.has("details-watched") or s.has("details-play"), what="les boutons de la fiche")
        self.to_button("details-watched")
        before = d.screen().description("details-watched")
        d.press("ok")
        d.wait_ui(lambda s: s.description("details-watched") != before, what="« vu » changé")
        d.press("ok", pause=0)
        d.wait_ui(lambda s: s.description("details-watched") == before, what="« vu » remis")
        return None

    def g15_poster_gallery(self, since):
        d = self.d
        self.to_button("details-more")
        mark = d.mark()
        d.press("ok")
        s = d.wait_ui(lambda s: s.has(prefix=MENU_LINE) and s, what="le menu ⋯")
        if not s.has("details-line-images"):
            d.press("back")
            d.wait_ui(lambda s: not s.has(prefix=MENU_LINE), what="le menu ⋯ fermé")
            raise Skip("pas de « Changer les images » (profil non administrateur)")
        for _ in range(12):
            if d.screen().focus_in("details-line-images"):
                break
            d.press("down")
        mark = d.mark()
        d.press("ok", pause=0)
        end = self.wait_screen("Images", mark)
        d.wait_ui(lambda s: s.has(prefix="images-image-") or s.has(prefix="images-type-"), what="la galerie")
        d.press("back")
        self.wait_screen("Details", mark)
        # Back on the details, its menu open or not: g16 closes it.
        d.wait_ui(lambda s: s.focus_tag("details-"), what="la fiche")
        return end

    def g16_back_to_card(self, since):
        d = self.d
        if getattr(self, "grid_card", None) is None or self.last_screen() != "Details":
            raise Skip("pas venu d'une grille (un geste précédent n'y a pas mené)")
        if d.screen().has(prefix=MENU_LINE):
            # A menu left open: « Retour » closes it, once it is gone the next one leaves the details.
            d.press("back")
            d.wait_ui(lambda s: not s.has(prefix=MENU_LINE), what="le menu ⋯ fermé")
        end = self.back_to("Library")
        focused = d.wait_ui(lambda s: s.focus_tag("item-"), what="la carte quittée")
        if focused != self.grid_card:
            raise Failed(f"focus sur {focused}, pas sur {self.grid_card}")
        return end

    def g17_search(self, since):
        d = self.d
        self.home()
        self.to_rail_entry("rail-search")
        mark = d.mark()
        d.press("ok")
        self.wait_screen("Search", mark)
        d.wait_ui(lambda s: s.focus_tag("key-"), what="le clavier")
        d.press("ok", pause=0)
        d.wait_ui(lambda s: s.has(prefix="search-"), timeout=20, what="des résultats")
        return None

    def g18_settings_page(self, since):
        d = self.d
        self.home()
        self.to_rail_entry("rail-settings")
        mark = d.mark()
        d.press("ok")
        self.wait_screen("Settings", mark)
        for _ in range(14):
            s = d.screen()
            if s.focus_in("setting-Subtitles"):
                break
            # The rail may keep the focus as the screen opens: → enters the list.
            d.press("right" if s.focus_tag("rail-") else "down")
        else:
            raise Failed("« Sous-titres » jamais atteint")
        mark = d.mark()
        d.press("ok", pause=0)
        return self.wait_screen("SettingsPage", mark)

    def g19_switch_profile(self, since):
        d = self.d
        self.home()
        self.to_rail_entry("rail-profiles")
        mark = d.mark()
        d.press("ok")
        self.wait_screen("Profiles", mark)
        d.wait_ui(lambda s: s.focus_tag("profile-"), what="un profil focalisé")
        mark = d.mark()
        d.press("ok", pause=0)
        screen, at = self.next_screen(mark, ("Home", "Pin", "QuickConnect", "Password"))
        if screen != "Home":
            self.start(cold=True)
            raise Skip(f"le profil focalisé demande un code ou une connexion ({screen})")
        return at

    def g20_next_up(self, since):
        d = self.d
        self.home()
        s = d.screen()
        rows = [n.get("resource-id") for n in s.nodes(prefix="row-")]
        next_up = next((r for r in rows if "next" in r.lower()), None)
        if next_up is None:
            raise Skip("pas de rangée « À suivre » à l'accueil")
        key = next_up.removeprefix("row-")
        for _ in range(8):
            d.press("down")
            if d.screen().focus_tag(f"card-{key}-"):
                break
        else:
            raise Failed("la rangée « À suivre » n'a jamais le focus")
        d.press("ok")
        d.wait_ui(lambda s: s.focus_in("details-play"), what="« Lire » de l'épisode")
        mark = d.mark()
        d.press("ok", pause=0)
        end = d.wait_event(lambda e: e == "playing", mark, 30, "lecture")
        # Not counted as seen: out at once, the position stays where it was within seconds.
        self.leave_player()
        return end

    # helpers of the player ------------------------------------------------------------------------

    def in_player(self, osd=False):
        """In the player, the picture playing, the interface hidden (or shown with [osd])."""
        d = self.d
        with d.lock:
            screens = [e for _, e in d.events if e.startswith("screen ")]
            moves = [e for _, e in d.events if e in ("playing", "paused", "buffering")]
            shown = [e for _, e in d.events if e.startswith("osd ")]
        if not screens or screens[-1] != "screen Player" or not moves:
            raise Skip("pas dans le lecteur (un geste précédent n'y a pas mené)")
        if moves[-1] == "paused":
            # Paused by the gesture before: OK on the progress bar plays again.
            mark = d.mark()
            if not shown or shown[-1] != "osd shown":
                d.press("ok")
            d.press("ok")
            d.wait_event(lambda e: e == "playing", mark, what="lecture")
        with d.lock:
            shown = [e for _, e in d.events if e.startswith("osd ")]
        visible = bool(shown) and shown[-1] == "osd shown"
        if osd and not visible:
            mark = d.mark()
            d.press("ok")
            d.wait_event(lambda e: e == "osd shown", mark, what="interface du lecteur")
        elif not osd and visible:
            # The interface hides itself after 4 s without a key.
            mark = d.mark()
            d.wait_event(lambda e: e == "osd hidden", mark, 8, "interface cachée")

    def leave_player(self):
        """« Retour » until the screen under the player is drawn: one key at a time, each given the
        time to land (a second « Retour » on the screen under would leave it too). A keyboard left
        attached to the window (seen on the emulator, after the sign-in tests) took three « Retour »
        in a row for itself once (essai réel 3, gesture 20): up to six, as a hand would."""
        d = self.d
        mark = d.mark()
        out = lambda e: e.startswith("screen ") and e != "screen Player"
        for _ in range(6):
            step = d.mark()
            d.press("back")
            # « Retour » first closes a panel or hides the interface (the player says so), then leaves.
            closed = lambda e: out(e) or e in ("osd hidden", "panel closed")
            try:
                d.wait_event(closed, step, 4, "sortie du lecteur")
            except Failed:
                continue
            with d.lock:
                left = [at for at, e in d.events[mark:] if out(e)]
            if left:
                return left[0]
        return d.wait_event(out, mark, what="sortie du lecteur")


# The lines of the menu ⋯ of a details screen: there only while it is open (the side panel keeps
# its own tag, `details-panel`, closed).
MENU_LINE = "details-line-"

# The buttons of the player, left to right (`PlayerScreen`; the episodes around « Lecture », chapters and
# quality only when they apply).
CONTROLS = [
    "player-previous-episode",
    "player-play",
    "player-next-episode",
    "player-chapters",
    "player-tracks",
    "player-subtitles",
    "player-quality",
    "player-settings",
    "player-info-button",
]

GESTURES = [
    # number, name, keys expected, target (s, response), method
    (1, "Démarrage à froid jusqu'à l'accueil", 1, 2.0, "g01_cold_start"),
    (2, "Accueil → fiche du premier titre à reprendre", 2, 0.3, "g02_resume_details"),
    (3, "Fiche → lecture (« Reprendre »)", 1, 2.0, "g03_play_from_details"),
    (4, "Afficher l'interface du lecteur", 1, 0.2, "g04_show_osd"),
    (5, "Pause, puis reprise", 2, 0.5, "g05_pause_resume"),
    (6, "Avancer de 30 s", 1, 1.5, "g06_jump"),
    (7, "Changer de sous-titres (et les remettre)", 9, 0.5, "g07_subtitles"),
    (8, "Quitter le lecteur", 2, 0.5, "g08_leave_player"),
    (9, "Fiche → accueil", 1, 0.3, "g09_details_to_home"),
    (10, "Lire le titre à la une", 1, 2.0, "g10_play_featured"),
    (11, "Ouvrir la première bibliothèque", 3, 0.5, "g11_open_library"),
    (12, "Descendre de cinq rangées dans la grille", 5, 0.3, "g12_scroll_grid"),
    (13, "Ouvrir une fiche depuis la grille", 1, 0.3, "g13_open_from_grid"),
    (14, "Marquer comme vu, puis non vu", 6, 0.3, "g14_watched"),
    (15, "Corriger une affiche : ouvrir la galerie (sans rien changer)", 10, 1.0, "g15_poster_gallery"),
    (16, "Retour à la grille, sur la carte quittée", 1, 0.3, "g16_back_to_card"),
    (17, "Rechercher : ouvrir, taper une lettre, voir les résultats", 4, 1.0, "g17_search"),
    (18, "Réglages → page « Sous-titres »", 8, 0.5, "g18_settings_page"),
    (19, "Changer de profil (« Qui regarde ? », même profil)", 4, 1.0, "g19_switch_profile"),
    (20, "« À suivre » : de l'accueil à la lecture de l'épisode", 4, 2.0, "g20_next_up"),
]


def seconds(value):
    return "—" if value is None else f"{value:.2f} s"


def table(results):
    lines = [
        "| # | Geste | Appuis (attendus) | Total | Réponse | Cible (réponse) | Résultat |",
        "|---|---|---|---|---|---|---|",
    ]
    for number, name, presses, expected, total, response, target, status, approximate in results:
        approx = "≈ " if approximate and total is not None else ""
        # One line in the table: the detail of a failure is in the output of the script.
        verdict = status.splitlines()[0].replace("|", "/")
        if response is not None and status.startswith("ok") and response > target:
            verdict = f"{status}, au-delà de la cible"
        lines.append(
            f"| {number} | {name} | {presses} ({expected}) | {approx}{seconds(total)} | "
            f"{approx}{seconds(response)} | {target:.1f} s | {verdict} |"
        )
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("serial")
    parser.add_argument("--package", default="fr.guim.cinedoury")
    parser.add_argument("--out", help="also write the table to this Markdown file")
    parser.add_argument("--only", help="gestures to play, as 1,2,5 (they may need the ones before)")
    args = parser.parse_args()

    device = Device(args.serial, args.package)
    model = device.shell("getprop ro.product.model").strip()
    release = device.shell("getprop ro.build.version.release").strip()
    version = re.search(r"versionName=(\S+)", device.shell(f"dumpsys package {args.package}"))
    if version is None:
        sys.exit(f"{args.package} is not installed on {args.serial}")
    couch = Couch(device)
    device.start_log()
    device.shell(f"dumpsys gfxinfo {args.package} reset", check=False)
    only = {int(n) for n in args.only.split(",")} if args.only else None
    try:
        for number, name, presses, target, method in GESTURES:
            if only and number not in only:
                continue
            print(f"{number:2d}. {name}…", flush=True)
            couch.run(number, name, presses, target, getattr(couch, method), from_start=number == 1)
            print(f"    {couch.results[-1][7]}", flush=True)
    finally:
        device.stop_log()
    gfx = device.shell(f"dumpsys gfxinfo {args.package}", check=False)
    summary = [
        line.strip()
        for line in gfx.splitlines()
        if re.match(r"\s*(Total frames rendered|Janky frames|\d+th percentile|Number |GPU \d+th)", line)
    ]
    report = (
        f"## Gestes au canapé : {model}, Android {release}, Ciné Doury {version.group(1)}\n\n"
        f"{table(couch.results)}\n\n"
        "Images de toute la séance (`dumpsys gfxinfo`) :\n\n"
        + "\n".join(f"- {line}" for line in summary[:16])
        + "\n"
    )
    print("\n" + report)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as out:
            out.write(report)
        # The whole log of the session beside it, for a gesture that failed.
        with open(args.out + ".logcat.txt", "w", encoding="utf-8") as out:
            out.write(device.adb("logcat", "-d", "-v", "epoch", check=False, timeout=60))
    failed = [r for r in couch.results if r[7].startswith("échec")]
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
