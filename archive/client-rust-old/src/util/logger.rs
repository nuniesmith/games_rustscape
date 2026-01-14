use log::LevelFilter;
use env_logger::Builder;
use std::io::Write;

pub fn init_logger(level: LevelFilter) {
    Builder::new()
        .format(|buf, record| {
            writeln!(
                buf,
                "[{} {}] {}",
                chrono::Local::now().format("%Y-%m-%d %H:%M:%S"),
                record.level(),
                record.args()
            )
        })
        .filter(None, level)
        .init();
}

