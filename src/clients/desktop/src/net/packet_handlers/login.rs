#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LoginResponse {
    Success,
    InvalidCredentials,
    AccountDisabled,
    WorldFull,
    Unknown(u8),
}

