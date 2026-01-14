#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct Location {
    pub x: u16,
    pub y: u16,
    pub z: u8,
}

impl Location {
    pub fn new(x: u16, y: u16, z: u8) -> Self {
        Self { x, y, z }
    }
    
    pub fn region_x(&self) -> u8 {
        ((self.x >> 6) & 0xFF) as u8
    }
    
    pub fn region_y(&self) -> u8 {
        ((self.y >> 6) & 0xFF) as u8
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Direction {
    North,
    NorthEast,
    East,
    SouthEast,
    South,
    SouthWest,
    West,
    NorthWest,
    None,
}

impl Direction {
    pub fn from_delta(dx: i16, dy: i16) -> Self {
        match (dx.signum(), dy.signum()) {
            (0, 1) => Direction::North,
            (1, 1) => Direction::NorthEast,
            (1, 0) => Direction::East,
            (1, -1) => Direction::SouthEast,
            (0, -1) => Direction::South,
            (-1, -1) => Direction::SouthWest,
            (-1, 0) => Direction::West,
            (-1, 1) => Direction::NorthWest,
            _ => Direction::None,
        }
    }
    
    pub fn to_walking_direction(&self) -> u8 {
        match self {
            Direction::North => 1,
            Direction::NorthEast => 2,
            Direction::East => 4,
            Direction::SouthEast => 7,
            Direction::South => 6,
            Direction::SouthWest => 5,
            Direction::West => 3,
            Direction::NorthWest => 0,
            Direction::None => 0xFF,
        }
    }
    
    pub fn to_running_direction(&self) -> u8 {
        match self {
            Direction::North => 0,
            Direction::NorthEast => 1,
            Direction::East => 2,
            Direction::SouthEast => 3,
            Direction::South => 4,
            Direction::SouthWest => 5,
            Direction::West => 6,
            Direction::NorthWest => 7,
            Direction::None => 0xFF,
        }
    }
}

