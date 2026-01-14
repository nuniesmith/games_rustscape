// Basic TypeScript placeholder
console.log('RS2009 Web Client loaded');

document.getElementById('login-button')?.addEventListener('click', () => {
    const username = (document.getElementById('username') as HTMLInputElement).value;
    const password = (document.getElementById('password') as HTMLInputElement).value;
    
    console.log();
    
    // Hide login form and show game UI
    document.getElementById('login-container')?.style.setProperty('display', 'none');
    document.getElementById('game-canvas')?.style.setProperty('display', 'block');
});

