pub mod artwork;
pub mod db;
pub mod duplicates;
pub mod metadata;
pub mod migration;
pub mod scanner;

pub use artwork::{extract_dominant_accent_color, save_cover_to_cache};
pub use db::{
    app_data_dir, Database, EqPresetRecord, FolderRecord, ListeningStatsSummary, PlayHistoryEntry,
    PlaylistRecord, TrackRecord,
};
pub use duplicates::{DuplicateDetector, DuplicateGroup, DuplicateMatchKind};
pub use metadata::{read_metadata, write_metadata, AudioMetadata, MetadataUpdate};
pub use migration::{legacy_data_dir, migrate_from_legacy_dir, migrate_legacy_v0261, MigrationReport};
pub use scanner::{is_supported_audio_file, scan_folders, ScanProgress, ScanResult, SUPPORTED_EXTENSIONS};
