/* eslint-disable @typescript-eslint/no-var-requires */
const webpack = require('webpack')
const path = require('path')
const WrmPlugin = require('atlassian-webresource-webpack-plugin')
const BundleAnalyzerPlugin =
  require('webpack-bundle-analyzer').BundleAnalyzerPlugin
const TerserPlugin = require('terser-webpack-plugin')
const WebpackBar = require('webpackbar')
const { CleanWebpackPlugin } = require('clean-webpack-plugin')
const { DuplicatesPlugin } = require('inspectpack/plugin')
const WRM_DEPENDENCIES_CONFIG = require(`./wrm-dependencies-conf.js`)
const postcssImportResolver = require('postcss-import-resolver')

const PLUGIN_NAME = 'myteam.bot'
const PLUGIN_KEY = 'ru.mail.jira.plugins.myteam' // current plugin key
const MVN_OUTPUT_DIR = path.join(__dirname, '..', 'target', 'classes') // atlassian mvn plugin classes output
const FRONTEND_SRC_DIR = path.join(__dirname, 'src') // directory with all frontend sources
const BUNDLE_OUTPUT_DIR_NAME = 'webpack_bundles' // directory which contains all build resources (bundles)
const FRONTEND_TARGET_DIR = path.join(
  MVN_OUTPUT_DIR,
  ...PLUGIN_KEY.split('.'),
  BUNDLE_OUTPUT_DIR_NAME
) // jira target dir for bundle outputs

//so this is an object which module.exports should return

const alias = {
  '@shared': path.resolve(FRONTEND_SRC_DIR, 'shared'),
  '@tanstack/react-query': path.resolve(
    require.resolve('@tanstack/react-query')
  ),
}

const config = {
  target: 'web',
  context: FRONTEND_SRC_DIR, // directory where to look for all entries
  entry: {
    'create-chat-panel': [
      path.join(FRONTEND_SRC_DIR, 'create-chat-panel', 'index.tsx'),
    ], // build entry point
    'chat-reminder': [
      path.join(FRONTEND_SRC_DIR, 'chat-reminder', 'index.tsx'),
    ], // build entry point
    'chat-settings-panel': [
      path.join(FRONTEND_SRC_DIR, 'chat-settings-panel', 'index.tsx'),
    ],
    'project-chat-settings-panel': [
      path.join(FRONTEND_SRC_DIR, 'project-chat-settings-panel', 'index.tsx'),
    ],
    'manage-filter-subscriptions-page': [
      path.join(
        FRONTEND_SRC_DIR,
        'manage-filter-subscriptions-page',
        'index.tsx'
      ),
    ],
    'access-request-configuration-page': [
      path.join(
        FRONTEND_SRC_DIR,
        'access-request-configuration-page',
        'index.tsx'
      ),
    ],
    'access-request-page': [
      path.join(FRONTEND_SRC_DIR, 'access-request-page', 'index.tsx'),
    ],
    'greenhopper-quick-create-in-epic-fix': [
      path.join(FRONTEND_SRC_DIR, 'greenhopper-quick-create-in-epic', 'index.tsx'),
    ],
  },
  module: {
    rules: [
      {
        test: /\.m?js/,
        resolve: {
          fullySpecified: false,
        },
      },
      {
        // more info about ts-loader configuration here: https://github.com/TypeStrong/ts-loader
        test: /\.(ts|tsx)$/, // compiles all TypeScript files
        use: [
          'babel-loader',
          {
            loader: 'ts-loader',
            options: { compilerOptions: { noEmit: false } },
          },
        ], // TypeScript loader for webpack
        exclude: /node_modules/, // excludes node_modules directory
      },
      {
        test: /\.(png)$/i,
        use: [
          {
            loader: 'url-loader',
          },
        ],
      },
      {
        test: /\.(pcss|css)$/i,
        use: [
          { loader: 'style-loader' },
          { loader: 'css-loader' },
          {
            loader: 'postcss-loader',
            options: {
              postcssOptions: {
                plugins: {
                  'postcss-import': {
                    resolve: postcssImportResolver({ alias }),
                  },
                },
              },
            },
          },
        ],
      },
    ],
  },
  plugins: [
    // Atlassian Web-Resource Webpack Plugin
    // configuration documentation: https://bitbucket.org/atlassianlabs/atlassian-webresource-webpack-plugin
    new WrmPlugin({
      pluginKey: PLUGIN_KEY, // current plugin key
      providedDependencies: WRM_DEPENDENCIES_CONFIG, // internal jira plugins web-resource dependencies
      contextMap: {
        'chat-settings-panel': [PLUGIN_KEY + '.' + 'chat.settings.panel'], // Specify in which web-resource context to include entrypoint resources
        'project-chat-settings-panel': [
          PLUGIN_KEY + '.' + 'project.chat.settings.panel',
        ],
        'create-chat-panel': ['jira.browse.project', 'jira.navigator.advanced'],
        'chat-reminder': ['atl.general'],
        'manage-filter-subscriptions-page': [
          PLUGIN_KEY + '.' + 'manage.filter.subscriptions.page',
        ],
        'access-request-configuration-page': [
          PLUGIN_KEY + '.' + 'access.request.configuration.page',
        ],
        'access-request-page': [PLUGIN_KEY + '.' + 'access.request.page'],
        'greenhopper-quick-create-in-epic-fix': ['jira.view.issue'],
      },
      verbose: false,
      xmlDescriptors: path.resolve(
        MVN_OUTPUT_DIR,
        'META-INF',
        'plugin-descriptors',
        'wr-webpack-bundles.xml'
      ), //An absolute filepath to where the generated XML should be output to
      locationPrefix:
        PLUGIN_KEY.split('.').join('/') + '/' + BUNDLE_OUTPUT_DIR_NAME, // Adds given prefix value to location attribute of resource node
    }),
    new WebpackBar(), // Elegant ProgressBar and Profiler for Webpack,
    new webpack.ProvidePlugin({
      process: 'process/browser',
    }),
  ],
  externals: {
    JIRA: 'JIRA',
    GH: 'GH',
    AJS: {
      var: 'AJS',
    },
    jquery: 'require("jquery")',
    'wrm/context-path': 'require("wrm/context-path")',
    'jira/api/projects': 'require("jira/api/projects")',
    'wrm/format': 'AJS.format',
  },
  resolve: {
    extensions: ['.ts', '.tsx', '.js'],
    alias: {
      ...alias,
      process: 'process/browser',
      // All i18n calls really invokes via @atlassian/wrm-react-i18n plugin
      // @atlassian/wrm-react-i18n configuration could be found here: https://www.npmjs.com/package/@atlassian/i18n-properties-loader
      i18n: '@atlassian/wrm-react-i18n',
    },
  },
  output: {
    // more info about webpack output config here: https://webpack.js.org/configuration/output/
    filename: '[name].js', // regular file, filename
    chunkFilename: '[name].js', // chunk filename
    sourceMapFilename: 'assets/[name].js._map', //source-map filename if has any
    path: path.resolve(FRONTEND_TARGET_DIR), // directory with all output files
    chunkLoadingGlobal: 'webpackChunk_' + PLUGIN_NAME,
  },
  optimization: {
    // default code-splitting here  via  internal webpack SplitChunksPlugin
    // more info here: https://webpack.js.org/plugins/split-chunks-plugin/#optimizationsplitchunks
    chunkIds: 'named',
    runtimeChunk: {
      name: 'manifest',
    },
    splitChunks: {
      cacheGroups: {
        vendor: {
          test: /[\\/]node_modules[\\/]/,
          name: 'vendors',
          priority: -20,
          chunks: 'all',
        },
      },
    },
  },
}

module.exports = (env, argv) => {
  const devMode = argv.mode === 'development'
  config.mode = argv.mode

  if (argv.mode === 'development') {
    config.watch = true
    config.watchOptions = {
      aggregateTimeout: 2000,
    }
    config.devtool = 'source-map'
  } else if (argv.mode === 'production') {
    config.devtool = false
    if (argv.analyze) {
      // Here we go if now is: yarn analyze command running
      config.plugins.push(new DuplicatesPlugin()) // Shows package duplicates during build, if has any
      config.plugins.push(new BundleAnalyzerPlugin()) // Shows bundle sizes analysis results tree on http://127.0.0.1:8888/
    }
    config.plugins.push(new CleanWebpackPlugin())
  }

  config.optimization = {
    ...config.optimization,
    minimizer: [
      new TerserPlugin({
        terserOptions: {
          mangle: {
            // Don't mangle usage of I18n.getText() function
            reserved: ['I18n', 'getText'], // Part of @atlassian/wrm-react-i18n configuration
          },
          parallel: true, // runs all js minification tasks in parallel threads
          sourceMap: !devMode,
        },
      }),
    ],
  }

  return config
}
