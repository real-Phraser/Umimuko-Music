use serde::{Deserialize, Serialize};
use std::collections::HashSet;

/// Lightweight track reference stored in the playback queue.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct TrackRef {
    pub id: String,
    pub path: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration_ms: u64,
    pub artwork_path: Option<String>,
}

/// Queue repeat behavior.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum RepeatMode {
    #[default]
    Off,
    All,
    One,
}

/// Editable playback queue supporting drag-reorder, multi-remove, play-next,
/// add-to-queue, repeat modes, and deterministic/seeded or PRNG shuffle.
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct EditableQueue {
    tracks: Vec<TrackRef>,
    current_index: Option<usize>,
    repeat_mode: RepeatMode,
    shuffled: bool,
    shuffle_order: Vec<usize>,
    shuffle_pos: Option<usize>,
    rng_state: u64,
}

impl Default for EditableQueue {
    fn default() -> Self {
        Self::new()
    }
}

impl EditableQueue {
    /// Create an empty queue.
    pub fn new() -> Self {
        Self {
            tracks: Vec::new(),
            current_index: None,
            repeat_mode: RepeatMode::Off,
            shuffled: false,
            shuffle_order: Vec::new(),
            shuffle_pos: None,
            rng_state: 0x9E37_79B9_7F4A_7C15,
        }
    }

    /// Create a queue initialized with `tracks` and an optional starting index.
    pub fn with_tracks(tracks: Vec<TrackRef>, start_index: Option<usize>) -> Self {
        let mut q = Self::new();
        q.set_tracks(tracks, start_index);
        q
    }

    /// Seed the internal PRNG (useful for deterministic testing).
    pub fn set_seed(&mut self, seed: u64) {
        self.rng_state = if seed == 0 { 1 } else { seed };
    }

    fn next_rand(&mut self) -> u64 {
        // xorshift64*
        let mut x = self.rng_state;
        x ^= x >> 12;
        x ^= x << 25;
        x ^= x >> 27;
        self.rng_state = x;
        x.wrapping_mul(0x2545_F491_4F6C_DD1D)
    }

    fn rebuild_shuffle_order(&mut self) {
        let len = self.tracks.len();
        if len == 0 {
            self.shuffle_order.clear();
            self.shuffle_pos = None;
            return;
        }
        let mut order: Vec<usize> = (0..len).collect();
        // Fisher-Yates shuffle
        for i in (1..len).rev() {
            let j = (self.next_rand() as usize) % (i + 1);
            order.swap(i, j);
        }
        // Keep current track at the front of the shuffle order if one is active.
        if let Some(curr) = self.current_index {
            if let Some(pos) = order.iter().position(|&idx| idx == curr) {
                order.swap(0, pos);
                self.shuffle_pos = Some(0);
            } else {
                self.shuffle_pos = None;
            }
        } else {
            self.shuffle_pos = None;
        }
        self.shuffle_order = order;
    }

    pub fn tracks(&self) -> &[TrackRef] {
        &self.tracks
    }

    pub fn len(&self) -> usize {
        self.tracks.len()
    }

    pub fn is_empty(&self) -> bool {
        self.tracks.is_empty()
    }

    pub fn current_index(&self) -> Option<usize> {
        self.current_index
    }

    pub fn current_track(&self) -> Option<&TrackRef> {
        self.current_index.and_then(|idx| self.tracks.get(idx))
    }

    pub fn repeat_mode(&self) -> RepeatMode {
        self.repeat_mode
    }

    pub fn set_repeat_mode(&mut self, mode: RepeatMode) {
        self.repeat_mode = mode;
    }

    /// Cycle repeat mode: Off -> All -> One -> Off.
    pub fn cycle_repeat_mode(&mut self) -> RepeatMode {
        self.repeat_mode = match self.repeat_mode {
            RepeatMode::Off => RepeatMode::All,
            RepeatMode::All => RepeatMode::One,
            RepeatMode::One => RepeatMode::Off,
        };
        self.repeat_mode
    }

    pub fn is_shuffled(&self) -> bool {
        self.shuffled
    }

    /// Enable or disable shuffle. Enabling generates a fresh shuffle order
    /// anchored at `current_index`.
    pub fn set_shuffled(&mut self, shuffled: bool) {
        if self.shuffled == shuffled {
            return;
        }
        self.shuffled = shuffled;
        if shuffled {
            self.rebuild_shuffle_order();
        } else {
            self.shuffle_order.clear();
            self.shuffle_pos = None;
        }
    }

    /// Replace all tracks in the queue.
    pub fn set_tracks(&mut self, tracks: Vec<TrackRef>, start_index: Option<usize>) {
        self.tracks = tracks;
        self.current_index = start_index.filter(|&i| i < self.tracks.len());
        if self.shuffled {
            self.rebuild_shuffle_order();
        }
    }

    /// Jump to `index` in `tracks`.
    pub fn set_current_index(&mut self, index: usize) -> Option<&TrackRef> {
        if index >= self.tracks.len() {
            return None;
        }
        self.current_index = Some(index);
        if self.shuffled {
            self.shuffle_pos = self.shuffle_order.iter().position(|&i| i == index);
        }
        self.tracks.get(index)
    }

    /// Drag-reorder: move item from `from` to `to`, keeping `current_index`
    /// pointing to the same track. Returns `true` if a move occurred.
    pub fn move_item(&mut self, from: usize, to: usize) -> bool {
        let len = self.tracks.len();
        if from >= len || to >= len || from == to {
            return false;
        }
        let item = self.tracks.remove(from);
        self.tracks.insert(to, item);

        if let Some(curr) = self.current_index {
            self.current_index = Some(remap_index_after_move(curr, from, to));
        }
        if self.shuffled {
            for idx in &mut self.shuffle_order {
                *idx = remap_index_after_move(*idx, from, to);
            }
        }
        true
    }

    /// Remove multiple tracks by their indices in `tracks`.
    /// Returns the number of tracks actually removed.
    pub fn remove_indices(&mut self, indices: &[usize]) -> usize {
        if indices.is_empty() || self.tracks.is_empty() {
            return 0;
        }
        let remove_set: HashSet<usize> = indices
            .iter()
            .copied()
            .filter(|&i| i < self.tracks.len())
            .collect();
        if remove_set.is_empty() {
            return 0;
        }

        // Build old_index -> new_index map for surviving tracks.
        let mut new_tracks = Vec::with_capacity(self.tracks.len() - remove_set.len());
        let mut old_to_new: Vec<Option<usize>> = vec![None; self.tracks.len()];
        for (old_idx, track) in self.tracks.drain(..).enumerate() {
            if !remove_set.contains(&old_idx) {
                old_to_new[old_idx] = Some(new_tracks.len());
                new_tracks.push(track);
            }
        }
        self.tracks = new_tracks;

        // Update current_index.
        self.current_index = match self.current_index {
            None => None,
            Some(_) if self.tracks.is_empty() => None,
            Some(old_curr) => {
                if let Some(mapped) = old_to_new[old_curr] {
                    Some(mapped)
                } else {
                    // Current track was removed: pick the first surviving track at or after old_curr,
                    // or fall back to the last surviving track.
                    (old_curr + 1..old_to_new.len())
                        .find_map(|i| old_to_new[i])
                        .or_else(|| Some(self.tracks.len() - 1))
                }
            }
        };

        if self.shuffled {
            self.shuffle_order = self
                .shuffle_order
                .iter()
                .filter_map(|&old_idx| old_to_new.get(old_idx).copied().flatten())
                .collect();
            self.shuffle_pos = self
                .current_index
                .and_then(|curr| self.shuffle_order.iter().position(|&i| i == curr));
        }

        remove_set.len()
    }

    /// Insert a track to play immediately after the current track (or at index 0 if none).
    pub fn play_next(&mut self, track: TrackRef) {
        let insert_idx = match self.current_index {
            Some(curr) => (curr + 1).min(self.tracks.len()),
            None => 0,
        };
        self.tracks.insert(insert_idx, track);

        if let Some(curr) = self.current_index {
            if insert_idx <= curr {
                self.current_index = Some(curr + 1);
            }
        }
        if self.shuffled {
            for idx in &mut self.shuffle_order {
                if *idx >= insert_idx {
                    *idx += 1;
                }
            }
            let pos = match self.shuffle_pos {
                Some(p) => (p + 1).min(self.shuffle_order.len()),
                None => 0,
            };
            self.shuffle_order.insert(pos, insert_idx);
        }
    }

    /// Append a track to the end of the queue.
    pub fn add_to_queue(&mut self, track: TrackRef) {
        let new_idx = self.tracks.len();
        self.tracks.push(track);
        if self.shuffled {
            self.shuffle_order.push(new_idx);
        }
    }

    /// Remove all tracks from the queue.
    pub fn clear(&mut self) {
        self.tracks.clear();
        self.current_index = None;
        self.shuffle_order.clear();
        self.shuffle_pos = None;
    }

    /// Peek at the next track index for automatic progression (e.g. gapless/crossfade)
    /// without mutating queue state.
    pub fn peek_next_auto(&self) -> Option<usize> {
        self.compute_next_index(false)
    }

    /// Advance to the next track (`manual == false` for end-of-track auto-advance).
    pub fn advance_next(&mut self, manual: bool) -> Option<&TrackRef> {
        let next_idx = self.compute_next_index(manual)?;
        self.set_current_index(next_idx)
    }

    /// Move to the previous track.
    pub fn advance_prev(&mut self) -> Option<&TrackRef> {
        if self.tracks.is_empty() {
            return None;
        }
        let prev_idx = if self.shuffled && !self.shuffle_order.is_empty() {
            match self.shuffle_pos {
                Some(pos) if pos > 0 => Some(self.shuffle_order[pos - 1]),
                Some(_) if self.repeat_mode == RepeatMode::All => {
                    self.shuffle_order.last().copied()
                }
                Some(_) => Some(self.shuffle_order[0]),
                None => self.shuffle_order.first().copied(),
            }
        } else {
            match self.current_index {
                Some(curr) if curr > 0 => Some(curr - 1),
                Some(_) if self.repeat_mode == RepeatMode::All => Some(self.tracks.len() - 1),
                Some(_) => Some(0),
                None => Some(0),
            }
        }?;
        self.set_current_index(prev_idx)
    }

    fn compute_next_index(&self, manual: bool) -> Option<usize> {
        if self.tracks.is_empty() {
            return None;
        }
        if !manual && self.repeat_mode == RepeatMode::One {
            return self.current_index.or(Some(0));
        }
        if self.shuffled && !self.shuffle_order.is_empty() {
            match self.shuffle_pos {
                Some(pos) if pos + 1 < self.shuffle_order.len() => {
                    Some(self.shuffle_order[pos + 1])
                }
                Some(_) if self.repeat_mode == RepeatMode::All => {
                    self.shuffle_order.first().copied()
                }
                Some(_) => None,
                None => self.shuffle_order.first().copied(),
            }
        } else {
            match self.current_index {
                Some(curr) if curr + 1 < self.tracks.len() => Some(curr + 1),
                Some(_) if self.repeat_mode == RepeatMode::All => Some(0),
                Some(_) => None,
                None => Some(0),
            }
        }
    }
}

fn remap_index_after_move(idx: usize, from: usize, to: usize) -> usize {
    if idx == from {
        to
    } else if from < to && idx > from && idx <= to {
        idx - 1
    } else if to < from && idx >= to && idx < from {
        idx + 1
    } else {
        idx
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_track(id: &str) -> TrackRef {
        TrackRef {
            id: id.to_string(),
            path: format!("/music/{id}.flac"),
            title: format!("Title {id}"),
            artist: "Artist".to_string(),
            album: "Album".to_string(),
            duration_ms: 180_000,
            artwork_path: None,
        }
    }

    fn sample_tracks(n: usize) -> Vec<TrackRef> {
        (0..n).map(|i| make_track(&format!("t{i}"))).collect()
    }

    #[test]
    fn test_move_item_preserves_current_track() {
        let mut q = EditableQueue::with_tracks(sample_tracks(5), Some(1));
        assert_eq!(q.current_track().unwrap().id, "t1");

        // Move t1 from index 1 to index 3
        assert!(q.move_item(1, 3));
        assert_eq!(q.current_index(), Some(3));
        assert_eq!(q.current_track().unwrap().id, "t1");
        let ids: Vec<&str> = q.tracks().iter().map(|t| t.id.as_str()).collect();
        assert_eq!(ids, vec!["t0", "t2", "t3", "t1", "t4"]);

        // Move t4 from index 4 to index 0 (before current_index 3 -> shifts to 4)
        assert!(q.move_item(4, 0));
        assert_eq!(q.current_index(), Some(4));
        assert_eq!(q.current_track().unwrap().id, "t1");
    }

    #[test]
    fn test_multi_remove_updates_current_and_shuffle() {
        let mut q = EditableQueue::with_tracks(sample_tracks(6), Some(2));
        q.set_seed(42);
        q.set_shuffled(true);

        // Remove indices 0, 2 (current!), and 4
        let removed = q.remove_indices(&[0, 2, 4, 99]);
        assert_eq!(removed, 3);
        let ids: Vec<&str> = q.tracks().iter().map(|t| t.id.as_str()).collect();
        assert_eq!(ids, vec!["t1", "t3", "t5"]);
        // Current was t2 (index 2), next surviving after 2 was t3 -> now at index 1
        assert_eq!(q.current_index(), Some(1));
        assert_eq!(q.current_track().unwrap().id, "t3");

        // Shuffle order should still contain every valid index 0..3 exactly once
        let mut sorted = q.shuffle_order.clone();
        sorted.sort_unstable();
        assert_eq!(sorted, vec![0, 1, 2]);
    }

    #[test]
    fn test_play_next_and_add_to_queue() {
        let mut q = EditableQueue::with_tracks(sample_tracks(3), Some(0));
        q.play_next(make_track("next1"));
        q.add_to_queue(make_track("end1"));

        let ids: Vec<&str> = q.tracks().iter().map(|t| t.id.as_str()).collect();
        assert_eq!(ids, vec!["t0", "next1", "t1", "t2", "end1"]);
        assert_eq!(q.current_index(), Some(0));
        assert_eq!(q.advance_next(true).unwrap().id, "next1");
    }

    #[test]
    fn test_repeat_modes() {
        let mut q = EditableQueue::with_tracks(sample_tracks(3), Some(2));

        // Repeat Off: stops at end
        q.set_repeat_mode(RepeatMode::Off);
        assert!(q.advance_next(false).is_none());
        assert_eq!(q.current_index(), Some(2));

        // Repeat All: wraps around
        q.set_repeat_mode(RepeatMode::All);
        assert_eq!(q.advance_next(false).unwrap().id, "t0");
        assert_eq!(q.advance_prev().unwrap().id, "t2");

        // Repeat One: stays on current for auto-advance, advances on manual
        q.set_repeat_mode(RepeatMode::One);
        q.set_current_index(1);
        assert_eq!(q.peek_next_auto(), Some(1));
        assert_eq!(q.advance_next(false).unwrap().id, "t1");
        assert_eq!(q.advance_next(true).unwrap().id, "t2");
    }

    #[test]
    fn test_shuffle_visits_all_tracks_once_and_keeps_current_first() {
        let mut q = EditableQueue::with_tracks(sample_tracks(5), Some(2));
        q.set_seed(12345);
        q.set_shuffled(true);

        assert_eq!(q.current_track().unwrap().id, "t2");
        let mut visited = vec![q.current_track().unwrap().id.clone()];
        while let Some(t) = q.advance_next(false) {
            visited.push(t.id.clone());
        }
        assert_eq!(visited.len(), 5);
        let unique: HashSet<String> = visited.into_iter().collect();
        assert_eq!(unique.len(), 5);
    }
}
