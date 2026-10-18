/**
 * Support channels. No payment SDK is involved: the app only opens a link or copies an address.
 */
export const REPO_URL = 'https://github.com/timmylich/wellness';
export const TELEGRAM_URL = 'https://t.me/timmylich_portfolio';

/** Tribute (cards, crypto, SBP): inside Telegram or in the browser. */
export const TRIBUTE_TELEGRAM_URL = 'https://t.me/tribute/app?startapp=dRVs';
export const TRIBUTE_WEB_URL = 'https://web.tribute.tg/d/RVs';

export interface Wallet {
  id: 'evm' | 'sol' | 'tron' | 'ton';
  /** Network names shown under the title. */
  networks: string;
  address: string;
}

export const WALLETS: Wallet[] = [
  // One EVM address works on every EVM-compatible network.
  { id: 'evm', networks: 'Ethereum · BNB Chain · Base · Arbitrum · Optimism · Polygon', address: '0xe790989b801ddbc706f391b10bec0e6c1a1fd334' },
  { id: 'sol', networks: 'Solana (SOL, USDC, USDT)', address: 'HJq4ocLn8eua95WKQs82N4hZ2S7VABANefTrSoUqo6rb' },
  { id: 'tron', networks: 'TRON (TRX, USDT TRC-20)', address: 'TMKejJ5a5unasnZiVZ1Vz8GvkXsDj7mvPE' },
  { id: 'ton', networks: 'TON · Gram (wallet W5)', address: 'UQCOymZAvj5ho73JlD1s1-qo-pD-bqIQ-H_Y4FEjX7Sp68rI' },
];
