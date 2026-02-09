class Camera {
    constructor() {
        this.x = 0;
        this.y = 0;
    }

    update(target, canvas) {
        if (!target) return;
        this.x = target.x - canvas.width / 2;
        this.y = target.y - canvas.height / 2;
    }
}

class Renderer {
    constructor(ctx, canvas) {
        this.ctx = ctx;
        this.canvas = canvas;
        this.MAP_SIZE = 3000;
        this.GRID_SIZE = 100;

        this.ui = {
            playerCount: document.getElementById('player-count'),
            currentScore: document.getElementById('current-score'),
            leaderboardList: document.getElementById('leaderboard-list'),
            hud: document.getElementById('hud'),
            lb: document.getElementById('leaderboard')
        };
    }

    draw(gameState, camera, myId, isMenu = false) {
        const { ctx, canvas } = this;
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        ctx.save();
        ctx.translate(-camera.x, -camera.y);

        this.drawGrid(camera);

        if (gameState.players) {
            const alivePlayers = gameState.players.filter(p => p.isAlive);
            alivePlayers.forEach(p => this.drawTerritory(p));
            alivePlayers.forEach(p => this.drawTrail(p));
            alivePlayers.forEach(p => this.drawHead(p));
        }

        ctx.restore();

        if (!isMenu) {
            this.updateUI(gameState, myId);
        }
    }

    drawTerritory(p) {
        if (!p.territory || p.territory.length === 0) return;
        const { ctx } = this;
        ctx.save();
        ctx.fillStyle = p.color;
        ctx.globalAlpha = 0.35;

        p.territory.forEach(polygon => {
            if (polygon.length < 3) return;
            ctx.beginPath();
            ctx.moveTo(polygon[0][0], polygon[0][1]);
            polygon.forEach(pt => ctx.lineTo(pt[0], pt[1]));
            ctx.closePath();
            ctx.fill();
        });
        ctx.restore();
    }

    drawTrail(p) {
        if (!p.trail || p.trail.length < 2) return;
        const { ctx } = this;
        ctx.save();
        ctx.strokeStyle = p.color;
        ctx.lineWidth = 5;
        ctx.lineCap = "round";
        ctx.lineJoin = "round";
        ctx.globalAlpha = 0.8;

        ctx.beginPath();
        ctx.moveTo(p.trail[0][0], p.trail[0][1]);
        p.trail.forEach(pt => ctx.lineTo(pt[0], pt[1]));
        ctx.lineTo(p.x, p.y);
        ctx.stroke();
        ctx.restore();
    }

    drawHead(p) {
        const { ctx } = this;
        ctx.save();
        ctx.translate(p.x, p.y);
        ctx.rotate(p.angle || 0);
        ctx.fillStyle = p.color;
        ctx.shadowBlur = 10;
        ctx.shadowColor = "rgba(0,0,0,0.3)";
        ctx.fillRect(-15, -15, 30, 30);
        ctx.strokeStyle = "white";
        ctx.lineWidth = 2;
        ctx.strokeRect(-15, -15, 30, 30);
        ctx.restore();

        if (p.name) this.drawNameplate(p.name, p.x, p.y - 35);
    }

    drawNameplate(name, x, y) {
        const { ctx } = this;
        ctx.font = "bold 14px 'Segoe UI', Arial";
        const textWidth = ctx.measureText(name).width;
        ctx.fillStyle = "rgba(0, 0, 0, 0.5)";
        ctx.beginPath();
        ctx.roundRect(x - textWidth / 2 - 8, y - 12, textWidth + 16, 20, 10);
        ctx.fill();
        ctx.fillStyle = "white";
        ctx.textAlign = "center";
        ctx.fillText(name, x, y + 3);
    }

    drawGrid(camera) {
        const { ctx, canvas } = this;
        ctx.save();
        ctx.fillStyle = "#fafafa";
        ctx.fillRect(0, 0, this.MAP_SIZE, this.MAP_SIZE);
        ctx.strokeStyle = "rgba(0, 0, 0, 0.05)";
        ctx.lineWidth = 1;

        const startX = Math.floor(camera.x / this.GRID_SIZE) * this.GRID_SIZE;
        const startY = Math.floor(camera.y / this.GRID_SIZE) * this.GRID_SIZE;

        ctx.beginPath();
        for (let x = startX; x <= startX + canvas.width + this.GRID_SIZE; x += this.GRID_SIZE) {
            if (x >= 0 && x <= this.MAP_SIZE) {
                ctx.moveTo(x, 0); ctx.lineTo(x, this.MAP_SIZE);
            }
        }
        for (let y = startY; y <= startY + canvas.height + this.GRID_SIZE; y += this.GRID_SIZE) {
            if (y >= 0 && y <= this.MAP_SIZE) {
                ctx.moveTo(0, y); ctx.lineTo(this.MAP_SIZE, y);
            }
        }
        ctx.stroke();
        ctx.restore();
    }

    updateUI(gameState, myId) {
        this.ui.hud.style.display = 'block';
        this.ui.lb.style.display = 'block';

        const count = (gameState.allPlayers !== undefined) ? gameState.allPlayers : gameState.players.length;
        this.ui.playerCount.innerText = `Players: ${count}`;

        const me = gameState.players.find(p => p.id === myId);
        if (me) {
            this.ui.currentScore.innerText = `Score: ${Math.round(me.score)}`;
        }

        if (gameState.leaderboard) {
            this.ui.leaderboardList.innerHTML = gameState.leaderboard.map((entry, i) => `
                <div class="leaderboard-entry" style="color: ${entry.color}">
                    <span>${i + 1}. <span class="leaderboard-name">${entry.name}</span></span>
                    <span>${Math.round(entry.score)}</span>
                </div>
            `).join('');
        }
    }
}

class MenuBot {
    constructor(w, h) {
        this.w = w; this.h = h;
        this.reset();
    }

    reset() {
        this.x = Math.random() * this.w;
        this.y = Math.random() * this.h;
        this.color = `hsl(${Math.random() * 360}, 70%, 60%)`;
        this.angle = Math.random() * Math.PI * 2;
        this.trail = [];
        this.state = 'STRAIGHT';
        this.timer = Math.random() * 100;
        this.turnSpeed = 0;
    }

    update() {
        this.timer--;
        if (this.timer <= 0) {
            if (this.state === 'STRAIGHT') {
                this.state = 'ATTACK';
                this.timer = 50 + Math.random() * 50;
                this.turnSpeed = Math.random() > 0.5 ? 0.04 : -0.04;
            } else {
                this.state = 'STRAIGHT';
                this.timer = 100 + Math.random() * 100;
                this.turnSpeed = 0;
                this.trail = [];
            }
        }

        this.angle += this.turnSpeed;
        this.x += Math.cos(this.angle) * 2.5;
        this.y += Math.sin(this.angle) * 2.5;

        if (this.x < 0 || this.x > this.w || this.y < 0 || this.y > this.h) {
            this.angle += Math.PI / 2;
        }

        if (this.state === 'ATTACK' && this.timer % 5 === 0) {
            this.trail.push([this.x, this.y]);
        }
    }
}

class Game {
    constructor() {
        this.canvas = document.getElementById('game-canvas');
        this.ctx = this.canvas.getContext('2d');
        this.renderer = new Renderer(this.ctx, this.canvas);
        this.camera = new Camera();
        this.ws = null;
        this.isPlaying = false;
        this.gameState = { players: [], allPlayers: 0 };
        this.myId = null;
        this.mouseX = 0; this.mouseY = 0;

        this.menuBots = Array.from({length: 5}, () => new MenuBot(window.innerWidth, window.innerHeight));

        this.initEventListeners();
        this.refreshStats();
        setInterval(() => this.refreshStats(), 5000);
        this.menuLoop();
    }

    async refreshStats() {
        if (this.isPlaying) return;
        try {
            const res = await fetch('/api/stats');
            const data = await res.json();
            document.getElementById('stat-players').innerText = data.players;
            document.getElementById('stat-rooms').innerText = data.rooms;
        } catch (e) { /* silent fail */ }
    }

    menuLoop() {
        if (this.isPlaying) return;

        this.renderer.draw({ players: [] }, { x: 0, y: 0 }, null, true);

        this.menuBots.forEach(bot => {
            bot.update();
            this.renderer.drawTrail(bot);
            this.renderer.drawHead(bot);
        });

        requestAnimationFrame(() => this.menuLoop());
    }

    initEventListeners() {
        window.addEventListener('resize', () => this.resize());
        this.resize();

        document.getElementById('play-button').addEventListener('click', () => this.start());
        document.getElementById('restart-button').addEventListener('click', () => {
            location.reload();
        });

        window.addEventListener('mousemove', (e) => {
            this.mouseX = e.clientX;
            this.mouseY = e.clientY;
        });
    }

    resize() {
        this.canvas.width = window.innerWidth;
        this.canvas.height = window.innerHeight;
    }

    start() {
        const nick = document.getElementById('nickname-input').value.trim();
        if (!nick) return alert("Nickname required");
        this.connect(nick);
    }

    connect(nick) {
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        this.ws = new WebSocket(`${protocol}//${location.host}/game?name=${encodeURIComponent(nick)}`);

        this.ws.onopen = () => {
            this.isPlaying = true;
            document.getElementById('login-screen').style.display = 'none';
            this.startInputLoop();
            this.gameLoop();
        };

        this.ws.onmessage = (e) => {
            const data = JSON.parse(e.data);
            if (data.type === "INIT") {
                this.myId = data.playerId;
            } else {
                console.log(data);

                const playersList = data.visiblePlayers || data.players || [];
                this.gameState = {
                    players: playersList,
                    allPlayers: data.allPlayers || playersList.length,
                    leaderboard: data.leaderboard || [],
                    timestamp: data.timestamp
                };
            }
        };

        this.ws.onclose = (e) => {
            this.isPlaying = false;
            if (e.code === 4000) this.showGameOver();
            else location.reload();
        };
    }

    startInputLoop() {
        setInterval(() => {
            if (this.ws?.readyState === WebSocket.OPEN) {
                this.ws.send(JSON.stringify({
                    x: this.mouseX + this.camera.x,
                    y: this.mouseY + this.camera.y
                }));
            }
        }, 50);
    }

    gameLoop() {
        if (!this.isPlaying) return;

        if (!this.gameState.players) {
            requestAnimationFrame(() => this.gameLoop());
            return;
        }

        const me = this.gameState.players.find(p => p.id === this.myId);

        if (me) {
            this.camera.update(me, this.canvas);
        }

        this.renderer.draw(this.gameState, this.camera, this.myId);
        requestAnimationFrame(() => this.gameLoop());
    }

    showGameOver() {
        const me = this.gameState.players.find(p => p.id === this.myId);
        document.getElementById('final-score').innerText = me ? Math.round(me.score) : "0";
        document.getElementById('game-over-screen').style.display = 'flex';
    }
}

new Game();