// Release builds drop console.log/info/debug (they cost time on the JS thread);
// warnings and errors stay for diagnostics.
module.exports = function (api) {
  api.cache(true);
  const plugins = [];
  if (process.env.NODE_ENV === 'production' || process.env.BABEL_ENV === 'production') {
    plugins.push(['transform-remove-console', { exclude: ['warn', 'error'] }]);
  }
  return { presets: ['babel-preset-expo'], plugins };
};
