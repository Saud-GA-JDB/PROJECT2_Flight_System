import unittest
from rewrite_compose import rewrite


class ReleaseComposeTests(unittest.TestCase):
    def setUp(self):
        self.services = ['aviation', 'identity', 'maintenance', 'notification', 'gateway']
        self.source = 'name: sauds-flight-ec2\nservices:\n'
        for service in self.services:
            self.source += f'  {service}:\n    image: sauds-flight-system-{service}:${{IMAGE_TAG:?Set IMAGE_TAG}}\n    pull_policy: never\n'
        self.source += '  redis:\n    image: redis:8.2-alpine\n# Password stays in ${SPRING_DATASOURCE_PASSWORD}\n'
        self.manifest = ''.join(f'{s}\t425989919753.dkr.ecr.eu-north-1.amazonaws.com/sauds-flight-system-{s}@sha256:{"a" * 64}\n' for s in self.services)

    def test_preserves_settings_and_infrastructure_images(self):
        result = rewrite(self.source, self.manifest)
        before = [x for x in self.source.splitlines() if 'image: sauds-flight-system-' not in x]
        after = [x for x in result.splitlines() if 'image: 425989919753' not in x]
        self.assertEqual(before, after)
        self.assertEqual(result.count('@sha256:'), 5)

    def test_can_release_again_from_pinned_images(self):
        first = rewrite(self.source, self.manifest)
        second = rewrite(first, self.manifest.replace('a' * 64, 'b' * 64))
        self.assertNotIn('a' * 64, second)
        self.assertEqual(second.count('b' * 64), 5)

    def test_rejects_wrong_registry(self):
        with self.assertRaises(ValueError):
            rewrite(self.source, self.manifest.replace('425989919753', '000000000000'))

    def test_rejects_incomplete_release(self):
        with self.assertRaises(ValueError):
            rewrite(self.source, '\n'.join(self.manifest.splitlines()[:-1]))

    def test_rejects_duplicate_service(self):
        with self.assertRaises(ValueError):
            rewrite(self.source, self.manifest + self.manifest.splitlines()[0] + '\n')

    def test_rejects_ambiguous_compose(self):
        with self.assertRaises(ValueError):
            rewrite(self.source + '    image: sauds-flight-system-aviation:old\n', self.manifest)


if __name__ == '__main__':
    unittest.main()
